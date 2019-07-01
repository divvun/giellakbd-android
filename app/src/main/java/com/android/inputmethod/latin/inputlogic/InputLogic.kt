/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.inputmethod.latin.inputlogic

import android.graphics.Color
import android.os.SystemClock
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.BackgroundColorSpan
import android.text.style.SuggestionSpan
import android.util.Log
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.inputmethod.CorrectionInfo
import android.view.inputmethod.EditorInfo
import com.android.inputmethod.compat.SuggestionSpanUtils
import com.android.inputmethod.event.*
import com.android.inputmethod.keyboard.Keyboard
import com.android.inputmethod.keyboard.KeyboardSwitcher
import com.android.inputmethod.latin.*
import com.android.inputmethod.latin.Dictionary
import com.android.inputmethod.latin.Suggest.OnGetSuggestedWordsCallback
import com.android.inputmethod.latin.SuggestedWords.SuggestedWordInfo
import com.android.inputmethod.latin.common.Constants
import com.android.inputmethod.latin.common.InputPointers
import com.android.inputmethod.latin.common.StringUtils
import com.android.inputmethod.latin.define.DebugFlags
import com.android.inputmethod.latin.settings.SettingsValues
import com.android.inputmethod.latin.settings.SettingsValuesForSuggestion
import com.android.inputmethod.latin.settings.SpacingAndPunctuations
import com.android.inputmethod.latin.suggestions.SuggestionStripViewAccessor
import com.android.inputmethod.latin.utils.*
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.sentry.Sentry
import no.divvun.domain.*
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * This class manages the input logic.
 */
class InputLogic
/**
 * Create a new instance of the input logic.
 * @param latinIME the instance of the parent LatinIME. We should remove this when we can.
 * @param suggestionStripViewAccessor an object to access the suggestion strip view.
 * @param dictionaryFacilitator facilitator for getting suggestions and updating user history
 * dictionary.
 */
(// TODO : Remove this member when we can.
        private val mLatinIME: LatinIME,
        private val mSuggestionStripViewAccessor: SuggestionStripViewAccessor,
        private val mDictionaryFacilitator: DictionaryFacilitator?) {

    // Never null.
    private var mInputLogicHandler = InputLogicHandler.NULL_HANDLER

    // TODO : make all these fields private as soon as possible.
    // Current space state of the input method. This can be any of the above constants.
    private var mSpaceState: Int = 0

    // Never null
    var mSuggestedWords = SuggestedWords.emptyInstance
    val suggest: Suggest

    private var lastComposedWord = LastComposedWord.NOT_A_COMPOSED_WORD
    // This has package visibility so it can be accessed from InputLogicHandler.
    val wordComposer: WordComposer
    val mConnection: RichInputConnection
    private val mRecapitalizeStatus = RecapitalizeStatus()

    private var mDeleteCount: Int = 0
    private var mLastKeyTime: Long = 0
    val mCurrentlyPressedHardwareKeys = TreeSet<Long>()

    // Keeps track of most recently inserted text (multi-character key) for reverting
    private var mEnteredText: String? = null

    // TODO: This boolean is persistent state and causes large side effects at unexpected times.
    // Find a way to remove it for readability.
    private var mIsAutoCorrectionIndicatorOn: Boolean = false
    private var mDoubleSpacePeriodCountdownStart: Long = 0

    // The word being corrected while the cursor is in the middle of the word.
    // Note: This does not have a composing span, so it must be handled separately.
    private var mWordBeingCorrectedByCursor: String? = null

    /* The sequence number member is only used in onUpdateBatchInput. It is increased each time
     * auto-commit happens. The reason we need this is, when auto-commit happens we trim the
     * input pointers that are held in a singleton, and to know how much to trim we rely on the
     * results of the suggestion process that is held in mSuggestedWords.
     * However, the suggestion process is asynchronous, and sometimes we may enter the
     * onUpdateBatchInput method twice without having recomputed suggestions yet, or having
     * received new suggestions generated from not-yet-trimmed input pointers. In this case, the
     * mIndexOfTouchPointOfSecondWords member will be out of date, and we must not use it lest we
     * remove an unrelated number of pointers (possibly even more than are left in the input
     * pointers, leading to a crash).
     * To avoid that, we increase the sequence number each time we auto-commit and trim the
     * input pointers, and we do not use any suggested words that have been generated with an
     * earlier sequence number.
     */
    private var mAutoCommitSequenceNumber = 1

    // Not recapitalizing at the moment
    val currentRecapitalizeState: Int
        get() = if (!mRecapitalizeStatus.isStarted || !mRecapitalizeStatus.isSetAt(mConnection.expectedSelectionStart,
                        mConnection.expectedSelectionEnd)) {
            RecapitalizeStatus.NOT_A_RECAPITALIZE_MODE
        } else mRecapitalizeStatus.currentMode

    /**
     * @return the editor info for the current editor
     */
    private val currentInputEditorInfo: EditorInfo?
        get() = mLatinIME.currentInputEditorInfo

    /**
     * @return the [Locale] of the [.mDictionaryFacilitator] if available. Otherwise
     * [Locale.ROOT].
     */
    private val dictionaryFacilitatorLocale: Locale
        get() = if (mDictionaryFacilitator != null) mDictionaryFacilitator.locale else Locale.ROOT

    /**
     * Gets an object allowing private IME commands to be sent to the
     * underlying editor.
     * @return An object for sending private commands to the underlying editor.
     */
    val privateCommandPerformer: PrivateCommandPerformer
        get() = mConnection

    /**
     * Gets the expected index of the first char of the composing span within the editor's text.
     * Returns a negative value in case there appears to be no valid composing span.
     *
     * @see .getComposingLength
     * @see RichInputConnection.hasSelection
     * @see RichInputConnection.isCursorPositionKnown
     * @see RichInputConnection.getExpectedSelectionStart
     * @see RichInputConnection.getExpectedSelectionEnd
     * @return The expected index in Java chars of the first char of the composing span.
     */
    // TODO: try and see if we can get rid of this method. Ideally the users of this class should
    // never need to know this.
    val composingStart: Int
        get() = if (!mConnection.isCursorPositionKnown || mConnection.hasSelection()) {
            -1
        } else mConnection.expectedSelectionStart - wordComposer.size()

    /**
     * Gets the expected length in Java chars of the composing span.
     * May be 0 if there is no valid composing span.
     * @see .getComposingStart
     * @return The expected length of the composing span.
     */
    // TODO: try and see if we can get rid of this method. Ideally the users of this class should
    // never need to know this.
    val composingLength: Int
        get() = wordComposer.size()

    init {


        val keyboardDescriptor = loadKeyboardDescriptor()
        // The dead key combiner is always active, and always first
        val combiners = listOf(DeadKeyCombiner(), SoftDeadKeyCombiner(keyboardDescriptor.transforms))
        wordComposer = WordComposer(combiners)
        mConnection = RichInputConnection(mLatinIME)
        mInputLogicHandler = InputLogicHandler.NULL_HANDLER
        suggest = Suggest(mDictionaryFacilitator)
    }

    /**
     * Initializes the input logic for input in an editor.
     *
     * Call this when input starts or restarts in some editor (typically, in onStartInputView).
     *
     * @param combiningSpec the combining spec string for this subtype
     * @param settingsValues the current settings values
     */
    fun startInput(combiningSpec: String?, settingsValues: SettingsValues) {
        mEnteredText = null
        mWordBeingCorrectedByCursor = null
        mConnection.onStartInput()
        if (!wordComposer.typedWord.isEmpty()) {
            // For messaging apps that offer send button, the IME does not get the opportunity
            // to capture the last word. This block should capture those uncommitted words.
            // The timestamp at which it is captured is not accurate but close enough.
            StatsUtils.onWordCommitUserTyped(
                    wordComposer.typedWord, wordComposer.isBatchMode)
        }
        wordComposer.restartCombining(combiningSpec)
        resetComposingState(true /* alsoResetLastComposedWord */)
        mDeleteCount = 0
        mSpaceState = SpaceState.NONE
        mRecapitalizeStatus.disable() // Do not perform recapitalize until the cursor is moved once
        mCurrentlyPressedHardwareKeys.clear()
        mSuggestedWords = SuggestedWords.emptyInstance
        // In some cases (namely, after rotation of the device) editorInfo.initialSelStart is lying
        // so we try using some heuristics to find out about these and fix them.
        mConnection.tryFixLyingCursorPosition()
        cancelDoubleSpacePeriodCountdown()
        if (InputLogicHandler.NULL_HANDLER === mInputLogicHandler) {
            mInputLogicHandler = InputLogicHandler(mLatinIME, this)
        } else {
            mInputLogicHandler.reset()
        }

        if (settingsValues.mShouldShowLxxSuggestionUi) {
            mConnection.requestCursorUpdates(true /* enableMonitor */,
                    true /* requestImmediateCallback */)
        }
    }

    /**
     * Call this when the subtype changes.
     * @param combiningSpec the spec string for the combining rules
     * @param settingsValues the current settings values
     */
    fun onSubtypeChanged(combiningSpec: String?, settingsValues: SettingsValues) {
        finishInput()
        startInput(combiningSpec, settingsValues)
    }

    /**
     * Call this when the orientation changes.
     * @param settingsValues the current values of the settings.
     */
    fun onOrientationChange(settingsValues: SettingsValues) {
        // If !isComposingWord, #commitTyped() is a no-op, but still, it's better to avoid
        // the useless IPC of {begin,end}BatchEdit.
        if (wordComposer.isComposingWord) {
            mConnection.beginBatchEdit()
            // If we had a composition in progress, we need to commit the word so that the
            // suggestionsSpan will be added. This will allow resuming on the same suggestions
            // after rotation is finished.
            commitTyped(settingsValues, LastComposedWord.NOT_A_SEPARATOR)
            mConnection.endBatchEdit()
        }
    }

    /**
     * Clean up the input logic after input is finished.
     */
    fun finishInput() {
        if (wordComposer.isComposingWord) {
            mConnection.finishComposingText()
            StatsUtils.onWordCommitUserTyped(
                    wordComposer.typedWord, wordComposer.isBatchMode)
        }
        resetComposingState(true /* alsoResetLastComposedWord */)
        mInputLogicHandler.reset()
    }

    // Normally this class just gets out of scope after the process ends, but in unit tests, we
    // create several instances of LatinIME in the same process, which results in several
    // instances of InputLogic. This cleans up the associated handler so that tests don't leak
    // handlers.
    fun recycle() {
        val inputLogicHandler = mInputLogicHandler
        mInputLogicHandler = InputLogicHandler.NULL_HANDLER
        inputLogicHandler.destroy()
        mDictionaryFacilitator?.closeDictionaries()
    }

    /**
     * React to a string input.
     *
     * This is triggered by keys that input many characters at once, like the ".com" key or
     * some additional keys for example.
     *
     * @param settingsValues the current values of the settings.
     * @param event the input event containing the data.
     * @return the complete transaction object
     */
    fun onTextInput(settingsValues: SettingsValues, event: Event,
                    keyboardShiftMode: Int, handler: LatinIME.UIHandler): InputTransaction {
        val rawText = event.textToCommit.toString()
        val inputTransaction = InputTransaction(settingsValues, event,
                SystemClock.uptimeMillis(), mSpaceState,
                getActualCapsMode(settingsValues, keyboardShiftMode))
        mConnection.beginBatchEdit()
        if (wordComposer.isComposingWord) {
            commitCurrentAutoCorrection(settingsValues, rawText, handler)
        } else {
            resetComposingState(true /* alsoResetLastComposedWord */)
        }
        handler.postUpdateSuggestionStrip(SuggestedWords.INPUT_STYLE_TYPING)
        val text = performSpecificTldProcessingOnTextInput(rawText)
        if (SpaceState.PHANTOM == mSpaceState) {
            insertAutomaticSpaceIfOptionsAndTextAllow(settingsValues)
        }
        mConnection.commitText(text, 1)
        StatsUtils.onWordCommitUserTyped(mEnteredText, wordComposer.isBatchMode)
        mConnection.endBatchEdit()
        // Space state must be updated before calling updateShiftState
        mSpaceState = SpaceState.NONE
        mEnteredText = text
        mWordBeingCorrectedByCursor = null
        inputTransaction.setDidAffectContents()
        inputTransaction.requireShiftUpdate(InputTransaction.SHIFT_UPDATE_NOW)
        return inputTransaction
    }

    /**
     * A suggestion was picked from the suggestion strip.
     * @param settingsValues the current values of the settings.
     * @param suggestionInfo the suggestion info.
     * @param keyboardShiftState the shift state of the keyboard, as returned by
     * [com.android.inputmethod.keyboard.KeyboardSwitcher.getKeyboardShiftMode]
     * @return the complete transaction object
     */
    // Called from {@link SuggestionStripView} through the {@link SuggestionStripView#Listener}
    // interface
    fun onPickSuggestionManually(settingsValues: SettingsValues,
                                 suggestionInfo: SuggestedWordInfo, keyboardShiftState: Int,
                                 currentKeyboardScriptId: Int, handler: LatinIME.UIHandler): InputTransaction {
        val suggestedWords = mSuggestedWords
        val suggestion = suggestionInfo.word
        // If this is a punctuation picked from the suggestion strip, pass it to onCodeInput
        if (suggestion.length == 1 && suggestedWords.isPunctuationSuggestions) {
            // We still want to log a suggestion click.
            StatsUtils.onPickSuggestionManually(
                    mSuggestedWords, suggestionInfo, mDictionaryFacilitator)
            // Word separators are suggested before the user inputs something.
            // Rely on onCodeInput to do the complicated swapping/stripping logic consistently.
            val event = Event.createPunctuationSuggestionPickedEvent(suggestionInfo)
            return onCodeInput(settingsValues, event, keyboardShiftState,
                    currentKeyboardScriptId, handler)
        }

        val event = Event.createSuggestionPickedEvent(suggestionInfo)
        val inputTransaction = InputTransaction(settingsValues,
                event, SystemClock.uptimeMillis(), mSpaceState, keyboardShiftState)
        // Manual pick affects the contents of the editor, so we take note of this. It's important
        // for the sequence of language switching.
        inputTransaction.setDidAffectContents()
        mConnection.beginBatchEdit()
        if (SpaceState.PHANTOM == mSpaceState && suggestion.isNotEmpty()
                // In the batch input mode, a manually picked suggested word should just replace
                // the current batch input text and there is no need for a phantom space.
                && !wordComposer.isBatchMode) {
            val firstChar = Character.codePointAt(suggestion, 0)
            if (!settingsValues.isWordSeparator(firstChar) || settingsValues.isUsuallyPrecededBySpace(firstChar)) {
                insertAutomaticSpaceIfOptionsAndTextAllow(settingsValues)
            }
        }

        // TODO: We should not need the following branch. We should be able to take the same
        // code path as for other kinds, use commitChosenWord, and do everything normally. We will
        // however need to reset the suggestion strip right away, because we know we can't take
        // the risk of calling commitCompletion twice because we don't know how the app will react.

        // TODO(rawa)
        // Disabled branch for onPickSuggestManually to work
        /**
        if (suggestionInfo.isKindOf(SuggestedWordInfo.KIND_APP_DEFINED)) {
            mSuggestedWords = SuggestedWords.emptyInstance
            mSuggestionStripViewAccessor.setNeutralSuggestionStrip()
            inputTransaction.requireShiftUpdate(InputTransaction.SHIFT_UPDATE_NOW)
            resetComposingState(true /* alsoResetLastComposedWord */)
            mConnection.commitCompletion(suggestionInfo.mApplicationSpecifiedCompletionInfo)
            mConnection.endBatchEdit()
            return inputTransaction
        }
         */

        commitChosenWord(settingsValues, suggestion, LastComposedWord.COMMIT_TYPE_MANUAL_PICK,
                LastComposedWord.NOT_A_SEPARATOR)
        mConnection.endBatchEdit()
        // Don't allow cancellation of manual pick
        lastComposedWord.deactivate()
        // Space state must be updated before calling updateShiftState
        mSpaceState = SpaceState.PHANTOM
        inputTransaction.requireShiftUpdate(InputTransaction.SHIFT_UPDATE_NOW)

        // If we're not showing the "Touch again to save", then update the suggestion strip.
        // That's going to be predictions (or punctuation suggestions), so INPUT_STYLE_NONE.
        handler.postUpdateSuggestionStrip(SuggestedWords.INPUT_STYLE_NONE)

        StatsUtils.onPickSuggestionManually(
                mSuggestedWords, suggestionInfo, mDictionaryFacilitator)
        StatsUtils.onWordCommitSuggestionPickedManually(
                suggestionInfo.word, wordComposer.isBatchMode)
        return inputTransaction
    }

    /**
     * Consider an update to the cursor position. Evaluate whether this update has happened as
     * part of normal typing or whether it was an explicit cursor move by the user. In any case,
     * do the necessary adjustments.
     * @param oldSelStart old selection start
     * @param oldSelEnd old selection end
     * @param newSelStart new selection start
     * @param newSelEnd new selection end
     * @param settingsValues the current values of the settings.
     * @return whether the cursor has moved as a result of user interaction.
     */
    fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int,
                          newSelStart: Int, newSelEnd: Int, settingsValues: SettingsValues): Boolean {
        if (mConnection.isBelatedExpectedUpdate(oldSelStart, newSelStart, oldSelEnd, newSelEnd)) {
            return false
        }
        // TODO: the following is probably better done in resetEntireInputState().
        // it should only happen when the cursor moved, and the very purpose of the
        // test below is to narrow down whether this happened or not. Likewise with
        // the call to updateShiftState.
        // We set this to NONE because after a cursor move, we don't want the space
        // state-related special processing to kick in.
        mSpaceState = SpaceState.NONE

        val selectionChangedOrSafeToReset = (oldSelStart != newSelStart || oldSelEnd != newSelEnd // selection changed

                || !wordComposer.isComposingWord) // safe to reset
        val hasOrHadSelection = oldSelStart != oldSelEnd || newSelStart != newSelEnd
        val moveAmount = newSelStart - oldSelStart
        // As an added small gift from the framework, it happens upon rotation when there
        // is a selection that we get a wrong cursor position delivered to startInput() that
        // does not get reflected in the oldSel{Start,End} parameters to the next call to
        // onUpdateSelection. In this case, we may have set a composition, and when we're here
        // we realize we shouldn't have. In theory, in this case, selectionChangedOrSafeToReset
        // should be true, but that is if the framework had taken that wrong cursor position
        // into account, which means we have to reset the entire composing state whenever there
        // is or was a selection regardless of whether it changed or not.
        if (hasOrHadSelection || !settingsValues.needsToLookupSuggestions()
                || selectionChangedOrSafeToReset && !wordComposer.moveCursorByAndReturnIfInsideComposingWord(moveAmount)) {
            // If we are composing a word and moving the cursor, we would want to set a
            // suggestion span for recorrection to work correctly. Unfortunately, that
            // would involve the keyboard committing some new text, which would move the
            // cursor back to where it was. Latin IME could then fix the position of the cursor
            // again, but the asynchronous nature of the calls results in this wreaking havoc
            // with selection on double tap and the like.
            // Another option would be to send suggestions each time we set the composing
            // text, but that is probably too expensive to do, so we decided to leave things
            // as is.
            // Also, we're posting a resume suggestions message, and this will update the
            // suggestions strip in a few milliseconds, so if we cleared the suggestion strip here
            // we'd have the suggestion strip noticeably janky. To avoid that, we don't clear
            // it here, which means we'll keep outdated suggestions for a split second but the
            // visual result is better.
            resetEntireInputState(newSelStart, newSelEnd, false /* clearSuggestionStrip */)
            // If the user is in the middle of correcting a word, we should learn it before moving
            // the cursor away.
            if (!TextUtils.isEmpty(mWordBeingCorrectedByCursor)) {
                val timeStampInSeconds = TimeUnit.MILLISECONDS.toSeconds(
                        System.currentTimeMillis()).toInt()
                mWordBeingCorrectedByCursor?.let {
                    performAdditionToUserHistoryDictionary(settingsValues, it,
                            NgramContext.EMPTY_PREV_WORDS_INFO)
                }
            }
        } else {
            // resetEntireInputState calls resetCachesUponCursorMove, but forcing the
            // composition to end. But in all cases where we don't reset the entire input
            // state, we still want to tell the rich input connection about the new cursor
            // position so that it can update its caches.
            mConnection.resetCachesUponCursorMoveAndReturnSuccess(
                    newSelStart, newSelEnd, false /* shouldFinishComposition */)
        }

        // The cursor has been moved : we now accept to perform recapitalization
        mRecapitalizeStatus.enable()
        // We moved the cursor. If we are touching a word, we need to resume suggestion.
        mLatinIME.mHandler.postResumeSuggestions(true /* shouldDelay */)
        // Stop the last recapitalization, if started.
        mRecapitalizeStatus.stop()
        mWordBeingCorrectedByCursor = null
        return true
    }

    /**
     * React to a code input. It may be a code point to insert, or a symbolic value that influences
     * the keyboard behavior.
     *
     * Typically, this is called whenever a key is pressed on the software keyboard. This is not
     * the entry point for gesture input; see the onBatchInput* family of functions for this.
     *
     * @param settingsValues the current settings values.
     * @param event the event to handle.
     * @param keyboardShiftMode the current shift mode of the keyboard, as returned by
     * [com.android.inputmethod.keyboard.KeyboardSwitcher.getKeyboardShiftMode]
     * @return the complete transaction object
     */
    fun onCodeInput(settingsValues: SettingsValues,
                    event: Event, keyboardShiftMode: Int,
                    currentKeyboardScriptId: Int, handler: LatinIME.UIHandler): InputTransaction {
        mWordBeingCorrectedByCursor = null
        val processedEvent = wordComposer.processEvent(event)
        val inputTransaction = InputTransaction(settingsValues,
                processedEvent, SystemClock.uptimeMillis(), mSpaceState,
                getActualCapsMode(settingsValues, keyboardShiftMode))
        if (processedEvent.mKeyCode != Constants.CODE_DELETE || inputTransaction.mTimestamp > mLastKeyTime + Constants.LONG_PRESS_MILLISECONDS) {
            mDeleteCount = 0
        }
        mLastKeyTime = inputTransaction.mTimestamp
        mConnection.beginBatchEdit()
        if (!wordComposer.isComposingWord) {
            // TODO: is this useful? It doesn't look like it should be done here, but rather after
            // a word is committed.
            mIsAutoCorrectionIndicatorOn = false
        }

        // TODO: Consolidate the double-space period timer, mLastKeyTime, and the space state.
        if (processedEvent.mCodePoint != Constants.CODE_SPACE) {
            cancelDoubleSpacePeriodCountdown()
        }

        var currentEvent: Event? = processedEvent
        while (null != currentEvent) {
            if (currentEvent.isConsumed) {
                handleConsumedEvent(currentEvent, inputTransaction)
            } else if (currentEvent.isFunctionalKeyEvent) {
                handleFunctionalEvent(currentEvent, inputTransaction, currentKeyboardScriptId,
                        handler)
            } else {
                handleNonFunctionalEvent(currentEvent, inputTransaction, handler)
            }
            currentEvent = currentEvent.mNextEvent
        }
        // Try to record the word being corrected when the user enters a word character or
        // the backspace key.
        if (!mConnection.hasSlowInputConnection() && !wordComposer.isComposingWord
                && (settingsValues.isWordCodePoint(processedEvent.mCodePoint) || processedEvent.mKeyCode == Constants.CODE_DELETE)) {
            mWordBeingCorrectedByCursor = getWordAtCursor(
                    settingsValues, currentKeyboardScriptId)
        }
        if (!inputTransaction.didAutoCorrect() && processedEvent.mKeyCode != Constants.CODE_SHIFT
                && processedEvent.mKeyCode != Constants.CODE_CAPSLOCK
                && processedEvent.mKeyCode != Constants.CODE_SWITCH_ALPHA_SYMBOL)
            lastComposedWord.deactivate()
        if (Constants.CODE_DELETE != processedEvent.mKeyCode) {
            mEnteredText = null
        }
        mConnection.endBatchEdit()
        return inputTransaction
    }

    fun onStartBatchInput(settingsValues: SettingsValues,
                          keyboardSwitcher: KeyboardSwitcher, handler: LatinIME.UIHandler) {
        mWordBeingCorrectedByCursor = null
        mInputLogicHandler.onStartBatchInput()
        handler.showGesturePreviewAndSuggestionStrip(
                SuggestedWords.emptyInstance, false /* dismissGestureFloatingPreviewText */)
        handler.cancelUpdateSuggestionStrip()
        ++mAutoCommitSequenceNumber
        mConnection.beginBatchEdit()
        if (wordComposer.isComposingWord) {
            if (wordComposer.isCursorFrontOrMiddleOfComposingWord) {
                // If we are in the middle of a recorrection, we need to commit the recorrection
                // first so that we can insert the batch input at the current cursor position.
                // We also need to unlearn the original word that is now being corrected.
                unlearnWord(wordComposer.typedWord, settingsValues,
                        Constants.EVENT_BACKSPACE)
                resetEntireInputState(mConnection.expectedSelectionStart,
                        mConnection.expectedSelectionEnd, true /* clearSuggestionStrip */)
            } else if (wordComposer.isSingleLetter) {
                // We auto-correct the previous (typed, not gestured) string iff it's one character
                // long. The reason for this is, even in the middle of gesture typing, you'll still
                // tap one-letter words and you want them auto-corrected (typically, "i" in English
                // should become "I"). However for any longer word, we assume that the reason for
                // tapping probably is that the word you intend to type is not in the dictionary,
                // so we do not attempt to correct, on the assumption that if that was a dictionary
                // word, the user would probably have gestured instead.
                commitCurrentAutoCorrection(settingsValues, LastComposedWord.NOT_A_SEPARATOR,
                        handler)
            } else {
                commitTyped(settingsValues, LastComposedWord.NOT_A_SEPARATOR)
            }
        }
        val codePointBeforeCursor = mConnection.codePointBeforeCursor
        if (Character.isLetterOrDigit(codePointBeforeCursor) || settingsValues.isUsuallyFollowedBySpace(codePointBeforeCursor)) {
            val autoShiftHasBeenOverriden = keyboardSwitcher.keyboardShiftMode != getCurrentAutoCapsState(settingsValues)
            mSpaceState = SpaceState.PHANTOM
            if (!autoShiftHasBeenOverriden) {
                // When we change the space state, we need to update the shift state of the
                // keyboard unless it has been overridden manually. This is happening for example
                // after typing some letters and a period, then gesturing; the keyboard is not in
                // caps mode yet, but since a gesture is starting, it should go in caps mode,
                // unless the user explictly said it should not.
                keyboardSwitcher.requestUpdatingShiftState(getCurrentAutoCapsState(settingsValues),
                        currentRecapitalizeState)
            }
        }
        mConnection.endBatchEdit()
        wordComposer.setCapitalizedModeAtStartComposingTime(
                getActualCapsMode(settingsValues, keyboardSwitcher.keyboardShiftMode))
    }

    fun onUpdateBatchInput(batchPointers: InputPointers) {
        mInputLogicHandler.onUpdateBatchInput(batchPointers, mAutoCommitSequenceNumber)
    }

    fun onEndBatchInput(batchPointers: InputPointers) {
        mInputLogicHandler.updateTailBatchInput(batchPointers, mAutoCommitSequenceNumber)
        ++mAutoCommitSequenceNumber
    }

    fun onCancelBatchInput(handler: LatinIME.UIHandler) {
        mInputLogicHandler.onCancelBatchInput()
        handler.showGesturePreviewAndSuggestionStrip(
                SuggestedWords.emptyInstance, true /* dismissGestureFloatingPreviewText */)
    }

    // TODO: on the long term, this method should become private, but it will be difficult.
    // Especially, how do we deal with InputMethodService.onDisplayCompletions?
    fun setSuggestedWords(suggestedWords: SuggestedWords) {
        if (!suggestedWords.isEmpty) {
            val suggestedWordInfo: SuggestedWordInfo?
            if (suggestedWords.mWillAutoCorrect) {
                suggestedWordInfo = suggestedWords.getInfo(SuggestedWords.INDEX_OF_AUTO_CORRECTION)
            } else {
                // We can't use suggestedWords.getWord(SuggestedWords.INDEX_OF_TYPED_WORD)
                // because it may differ from wordComposer.mTypedWord.
                suggestedWordInfo = suggestedWords.typedWordInfo
            }
            wordComposer.setAutoCorrection(suggestedWordInfo)
        }
        mSuggestedWords = suggestedWords
        val newAutoCorrectionIndicator = suggestedWords.mWillAutoCorrect

        // Put a blue underline to a word in TextView which will be auto-corrected.
        if (mIsAutoCorrectionIndicatorOn != newAutoCorrectionIndicator && wordComposer.isComposingWord) {
            mIsAutoCorrectionIndicatorOn = newAutoCorrectionIndicator
            val textWithUnderline = getTextWithUnderline(wordComposer.typedWord)
            // TODO: when called from an updateSuggestionStrip() call that results from a posted
            // message, this is called outside any batch edit. Potentially, this may result in some
            // janky flickering of the screen, although the display speed makes it unlikely in
            // the practice.
            setComposingTextInternal(textWithUnderline, 1)
        }
    }

    /**
     * Handle a consumed event.
     *
     * Consumed events represent events that have already been consumed, typically by the
     * combining chain.
     *
     * @param event The event to handle.
     * @param inputTransaction The transaction in progress.
     */
    private fun handleConsumedEvent(event: Event, inputTransaction: InputTransaction) {
        // A consumed event may have text to commit and an update to the composing state, so
        // we evaluate both. With some combiners, it's possible than an event contains both
        // and we enter both of the following if clauses.
        val textToCommit = event.textToCommit
        if (!TextUtils.isEmpty(textToCommit)) {
            mConnection.commitText(textToCommit, 1)
            inputTransaction.setDidAffectContents()
        }
        if (wordComposer.isComposingWord) {
            setComposingTextInternal(wordComposer.typedWord, 1)
            inputTransaction.setDidAffectContents()
            inputTransaction.setRequiresUpdateSuggestions()
        }
    }

    /**
     * Handle a functional key event.
     *
     * A functional event is a special key, like delete, shift, emoji, or the settings key.
     * Non-special keys are those that generate a single code point.
     * This includes all letters, digits, punctuation, separators, emoji. It excludes keys that
     * manage keyboard-related stuff like shift, language switch, settings, layout switch, or
     * any key that results in multiple code points like the ".com" key.
     *
     * @param event The event to handle.
     * @param inputTransaction The transaction in progress.
     */
    private fun handleFunctionalEvent(event: Event, inputTransaction: InputTransaction,
                                      currentKeyboardScriptId: Int, handler: LatinIME.UIHandler) {
        when (event.mKeyCode) {
            Constants.CODE_DELETE -> {
                handleBackspaceEvent(event, inputTransaction, currentKeyboardScriptId)
                // Backspace is a functional key, but it affects the contents of the editor.
                inputTransaction.setDidAffectContents()
            }
            Constants.CODE_SHIFT -> {
                performRecapitalization(inputTransaction.mSettingsValues)
                inputTransaction.requireShiftUpdate(InputTransaction.SHIFT_UPDATE_NOW)
                if (mSuggestedWords.isPrediction) {
                    inputTransaction.setRequiresUpdateSuggestions()
                }
            }
            Constants.CODE_CAPSLOCK -> {
            }
            Constants.CODE_SYMBOL_SHIFT -> {
            }
            Constants.CODE_SWITCH_ALPHA_SYMBOL -> {
            }
            Constants.CODE_SETTINGS -> onSettingsKeyPressed()
            Constants.CODE_SHORTCUT -> {
            }
            Constants.CODE_ACTION_NEXT -> performEditorAction(EditorInfo.IME_ACTION_NEXT)
            Constants.CODE_ACTION_PREVIOUS -> performEditorAction(EditorInfo.IME_ACTION_PREVIOUS)
            Constants.CODE_LANGUAGE_SWITCH -> handleLanguageSwitchKey()
            Constants.CODE_EMOJI -> {
            }
            Constants.CODE_ALPHA_FROM_EMOJI -> {
            }
            Constants.CODE_SHIFT_ENTER -> {
                val tmpEvent = Event.createSoftwareKeypressEvent(Constants.CODE_ENTER,
                        event.mKeyCode, event.mX, event.mY, event.isKeyRepeat, event.isDead)
                handleNonSpecialCharacterEvent(tmpEvent, inputTransaction, handler)
                // Shift + Enter is treated as a functional key but it results in adding a new
                // line, so that does affect the contents of the editor.
                inputTransaction.setDidAffectContents()
            }
            Constants.CODE_OUTPUT_TEXT -> {
                // TODO Validate correctness of use of CODE_OUTPUT_TEXT (See:
                mLatinIME.onTextInput(event.textToCommit.toString())
            }
            else -> throw RuntimeException("Unknown key code : " + event.mKeyCode)
        }// Note: Changing keyboard to shift lock state is handled in
        // {@link KeyboardSwitcher#onEvent(Event)}.
        // Note: Calling back to the keyboard on the symbol Shift key is handled in
        // {@link #onPressKey(int,int,boolean)} and {@link #onReleaseKey(int,boolean)}.
        // Note: Calling back to the keyboard on symbol key is handled in
        // {@link #onPressKey(int,int,boolean)} and {@link #onReleaseKey(int,boolean)}.
        // We need to switch to the shortcut IME. This is handled by LatinIME since the
        // input logic has no business with IME switching.
        // Note: Switching emoji keyboard is being handled in
        // {@link KeyboardState#onEvent(Event,int)}.
        // Note: Switching back from Emoji keyboard to the main keyboard is being
        // handled in {@link KeyboardState#onEvent(Event,int)}.
    }

    /**
     * Handle an event that is not a functional event.
     *
     * These events are generally events that cause input, but in some cases they may do other
     * things like trigger an editor action.
     *
     * @param event The event to handle.
     * @param inputTransaction The transaction in progress.
     */
    private fun handleNonFunctionalEvent(event: Event,
                                         inputTransaction: InputTransaction,
                                         handler: LatinIME.UIHandler) {
        inputTransaction.setDidAffectContents()
        when (event.mCodePoint) {
            Constants.CODE_ENTER -> {
                val editorInfo = currentInputEditorInfo
                val imeOptionsActionId = InputTypeUtils.getImeOptionsActionIdFromEditorInfo(editorInfo!!)
                if (InputTypeUtils.IME_ACTION_CUSTOM_LABEL == imeOptionsActionId) {
                    // Either we have an actionLabel and we should performEditorAction with
                    // actionId regardless of its value.
                    performEditorAction(editorInfo.actionId)
                } else if (EditorInfo.IME_ACTION_NONE != imeOptionsActionId) {
                    // We didn't have an actionLabel, but we had another action to execute.
                    // EditorInfo.IME_ACTION_NONE explicitly means no action. In contrast,
                    // EditorInfo.IME_ACTION_UNSPECIFIED is the default value for an action, so it
                    // means there should be an action and the app didn't bother to set a specific
                    // code for it - presumably it only handles one. It does not have to be treated
                    // in any specific way: anything that is not IME_ACTION_NONE should be sent to
                    // performEditorAction.
                    performEditorAction(imeOptionsActionId)
                } else {
                    // No action label, and the action from imeOptions is NONE: this is a regular
                    // enter key that should input a carriage return.
                    handleNonSpecialCharacterEvent(event, inputTransaction, handler)
                }
            }
            else -> handleNonSpecialCharacterEvent(event, inputTransaction, handler)
        }
    }

    /**
     * Handle inputting a code point to the editor.
     *
     * Non-special keys are those that generate a single code point.
     * This includes all letters, digits, punctuation, separators, emoji. It excludes keys that
     * manage keyboard-related stuff like shift, language switch, settings, layout switch, or
     * any key that results in multiple code points like the ".com" key.
     *
     * @param event The event to handle.
     * @param inputTransaction The transaction in progress.
     */
    private fun handleNonSpecialCharacterEvent(event: Event,
                                               inputTransaction: InputTransaction,
                                               handler: LatinIME.UIHandler) {
        val codePoint = event.mCodePoint
        mSpaceState = SpaceState.NONE
        if (inputTransaction.mSettingsValues.isWordSeparator(codePoint) || Character.getType(codePoint) == Character.OTHER_SYMBOL.toInt()) {
            handleSeparatorEvent(event, inputTransaction, handler)
        } else {
            if (SpaceState.PHANTOM == inputTransaction.mSpaceState) {
                if (wordComposer.isCursorFrontOrMiddleOfComposingWord) {
                    // If we are in the middle of a recorrection, we need to commit the recorrection
                    // first so that we can insert the character at the current cursor position.
                    // We also need to unlearn the original word that is now being corrected.
                    unlearnWord(wordComposer.typedWord, inputTransaction.mSettingsValues,
                            Constants.EVENT_BACKSPACE)
                    resetEntireInputState(mConnection.expectedSelectionStart,
                            mConnection.expectedSelectionEnd, true /* clearSuggestionStrip */)
                } else {
                    commitTyped(inputTransaction.mSettingsValues, LastComposedWord.NOT_A_SEPARATOR)
                }
            }
            handleNonSeparatorEvent(event, inputTransaction.mSettingsValues, inputTransaction)
        }
    }

    /**
     * Handle a non-separator.
     * @param event The event to handle.
     * @param settingsValues The current settings values.
     * @param inputTransaction The transaction in progress.
     */
    private fun handleNonSeparatorEvent(event: Event, settingsValues: SettingsValues,
                                        inputTransaction: InputTransaction) {
        val codePoint = event.mCodePoint
        // TODO: refactor this method to stop flipping isComposingWord around all the time, and
        // make it shorter (possibly cut into several pieces). Also factor
        // handleNonSpecialCharacterEvent which has the same name as other handle* methods but is
        // not the same.
        var isComposingWord = wordComposer.isComposingWord

        // TODO: remove isWordConnector() and use isUsuallyFollowedBySpace() instead.
        // See onStartBatchInput() to see how to do it.
        if (SpaceState.PHANTOM == inputTransaction.mSpaceState && !settingsValues.isWordConnector(codePoint)) {
            if (isComposingWord) {
                // Sanity check
                throw RuntimeException("Should not be composing here")
            }
            insertAutomaticSpaceIfOptionsAndTextAllow(settingsValues)
        }

        if (wordComposer.isCursorFrontOrMiddleOfComposingWord) {
            // If we are in the middle of a recorrection, we need to commit the recorrection
            // first so that we can insert the character at the current cursor position.
            // We also need to unlearn the original word that is now being corrected.
            unlearnWord(wordComposer.typedWord, inputTransaction.mSettingsValues,
                    Constants.EVENT_BACKSPACE)
            resetEntireInputState(mConnection.expectedSelectionStart,
                    mConnection.expectedSelectionEnd, true /* clearSuggestionStrip */)
            isComposingWord = false
        }
        // We want to find out whether to start composing a new word with this character. If so,
        // we need to reset the composing state and switch isComposingWord. The order of the
        // tests is important for good performance.
        // We only start composing if we're not already composing.
        if (!isComposingWord
                // We only start composing if this is a word code point. Essentially that means it's a
                // a letter or a word connector.
                && settingsValues.isWordCodePoint(codePoint)
                // We never go into composing state if suggestions are not requested.
                && settingsValues.needsToLookupSuggestions() &&
                // In languages with spaces, we only start composing a word when we are not already
                // touching a word. In languages without spaces, the above conditions are sufficient.
                // NOTE: If the InputConnection is slow, we skip the text-after-cursor check since it
                // can incur a very expensive getTextAfterCursor() lookup, potentially making the
                // keyboard UI slow and non-responsive.
                // TODO: Cache the text after the cursor so we don't need to go to the InputConnection
                // each time. We are already doing this for getTextBeforeCursor().
                (!settingsValues.mSpacingAndPunctuations.mCurrentLanguageHasSpaces || !/* checkTextAfter */mConnection.isCursorTouchingWord(settingsValues.mSpacingAndPunctuations,
                        !mConnection.hasSlowInputConnection()))) {
            // Reset entirely the composing state anyway, then start composing a new word unless
            // the character is a word connector. The idea here is, word connectors are not
            // separators and they should be treated as normal characters, except in the first
            // position where they should not start composing a word.
            isComposingWord = !settingsValues.mSpacingAndPunctuations.isWordConnector(codePoint)
            // Here we don't need to reset the last composed word. It will be reset
            // when we commit this one, if we ever do; if on the other hand we backspace
            // it entirely and resume suggestions on the previous word, we'd like to still
            // have touch coordinates for it.
            resetComposingState(false /* alsoResetLastComposedWord */)
        }
        if (isComposingWord) {
            wordComposer.applyProcessedEvent(event)
            // If it's the first letter, make note of auto-caps state
            if (wordComposer.isSingleLetter) {
                wordComposer.setCapitalizedModeAtStartComposingTime(inputTransaction.mShiftState)
            }
            setComposingTextInternal(getTextWithUnderline(wordComposer.typedWord), 1)
        } else {
            val swapWeakSpace = tryStripSpaceAndReturnWhetherShouldSwapInstead(event,
                    inputTransaction)

            if (swapWeakSpace && trySwapSwapperAndSpace(event, inputTransaction)) {
                mSpaceState = SpaceState.WEAK
            } else {
                sendKeyCodePoint(settingsValues, codePoint)
            }
        }
        inputTransaction.setRequiresUpdateSuggestions()
    }

    /**
     * Handle input of a separator code point.
     * @param event The event to handle.
     * @param inputTransaction The transaction in progress.
     */
    private fun handleSeparatorEvent(event: Event, inputTransaction: InputTransaction,
                                     handler: LatinIME.UIHandler) {
        val codePoint = event.mCodePoint
        val settingsValues = inputTransaction.mSettingsValues
        val wasComposingWord = wordComposer.isComposingWord
        // We avoid sending spaces in languages without spaces if we were composing.
        val shouldAvoidSendingCode = (Constants.CODE_SPACE == codePoint
                && !settingsValues.mSpacingAndPunctuations.mCurrentLanguageHasSpaces
                && wasComposingWord)
        if (wordComposer.isCursorFrontOrMiddleOfComposingWord) {
            // If we are in the middle of a recorrection, we need to commit the recorrection
            // first so that we can insert the separator at the current cursor position.
            // We also need to unlearn the original word that is now being corrected.
            unlearnWord(wordComposer.typedWord, inputTransaction.mSettingsValues,
                    Constants.EVENT_BACKSPACE)
            resetEntireInputState(mConnection.expectedSelectionStart,
                    mConnection.expectedSelectionEnd, true /* clearSuggestionStrip */)
        }
        // isComposingWord() may have changed since we stored wasComposing
        if (wordComposer.isComposingWord) {
            if (settingsValues.mAutoCorrectionEnabledPerUserSettings) {
                val separator = if (shouldAvoidSendingCode)
                    LastComposedWord.NOT_A_SEPARATOR
                else
                    StringUtils.newSingleCodePointString(codePoint)
                commitCurrentAutoCorrection(settingsValues, separator, handler)
                inputTransaction.setDidAutoCorrect()
            } else {
                commitTyped(settingsValues,
                        StringUtils.newSingleCodePointString(codePoint))
            }
        }

        val swapWeakSpace = tryStripSpaceAndReturnWhetherShouldSwapInstead(event,
                inputTransaction)

        val isInsideDoubleQuoteOrAfterDigit = Constants.CODE_DOUBLE_QUOTE == codePoint && mConnection.isInsideDoubleQuoteOrAfterDigit

        val needsPrecedingSpace: Boolean
        if (SpaceState.PHANTOM != inputTransaction.mSpaceState) {
            needsPrecedingSpace = false
        } else if (Constants.CODE_DOUBLE_QUOTE == codePoint) {
            // Double quotes behave like they are usually preceded by space iff we are
            // not inside a double quote or after a digit.
            needsPrecedingSpace = !isInsideDoubleQuoteOrAfterDigit
        } else if (settingsValues.mSpacingAndPunctuations.isClusteringSymbol(codePoint) && settingsValues.mSpacingAndPunctuations.isClusteringSymbol(
                        mConnection.codePointBeforeCursor)) {
            needsPrecedingSpace = false
        } else {
            needsPrecedingSpace = settingsValues.isUsuallyPrecededBySpace(codePoint)
        }

        if (needsPrecedingSpace) {
            insertAutomaticSpaceIfOptionsAndTextAllow(settingsValues)
        }

        if (tryPerformDoubleSpacePeriod(event, inputTransaction)) {
            mSpaceState = SpaceState.DOUBLE
            inputTransaction.setRequiresUpdateSuggestions()
            StatsUtils.onDoubleSpacePeriod()
        } else if (swapWeakSpace && trySwapSwapperAndSpace(event, inputTransaction)) {
            mSpaceState = SpaceState.SWAP_PUNCTUATION
            mSuggestionStripViewAccessor.setNeutralSuggestionStrip()
        } else if (Constants.CODE_SPACE == codePoint) {
            if (!mSuggestedWords.isPunctuationSuggestions) {
                mSpaceState = SpaceState.WEAK
            }

            startDoubleSpacePeriodCountdown(inputTransaction)
            if (wasComposingWord || mSuggestedWords.isEmpty) {
                inputTransaction.setRequiresUpdateSuggestions()
            }

            if (!shouldAvoidSendingCode) {
                sendKeyCodePoint(settingsValues, codePoint)
            }
        } else {
            if (SpaceState.PHANTOM == inputTransaction.mSpaceState && settingsValues.isUsuallyFollowedBySpace(codePoint) || Constants.CODE_DOUBLE_QUOTE == codePoint && isInsideDoubleQuoteOrAfterDigit) {
                // If we are in phantom space state, and the user presses a separator, we want to
                // stay in phantom space state so that the next keypress has a chance to add the
                // space. For example, if I type "Good dat", pick "day" from the suggestion strip
                // then insert a comma and go on to typing the next word, I want the space to be
                // inserted automatically before the next word, the same way it is when I don't
                // input the comma. A double quote behaves like it's usually followed by space if
                // we're inside a double quote.
                // The case is a little different if the separator is a space stripper. Such a
                // separator does not normally need a space on the right (that's the difference
                // between swappers and strippers), so we should not stay in phantom space state if
                // the separator is a stripper. Hence the additional test above.
                mSpaceState = SpaceState.PHANTOM
            }

            sendKeyCodePoint(settingsValues, codePoint)

            // Set punctuation right away. onUpdateSelection will fire but tests whether it is
            // already displayed or not, so it's okay.
            mSuggestionStripViewAccessor.setNeutralSuggestionStrip()
        }

        inputTransaction.requireShiftUpdate(InputTransaction.SHIFT_UPDATE_NOW)
    }

    /**
     * Handle a press on the backspace key.
     * @param event The event to handle.
     * @param inputTransaction The transaction in progress.
     */
    private fun handleBackspaceEvent(event: Event, inputTransaction: InputTransaction,
                                     currentKeyboardScriptId: Int) {
        mSpaceState = SpaceState.NONE
        mDeleteCount++

        // In many cases after backspace, we need to update the shift state. Normally we need
        // to do this right away to avoid the shift state being out of date in case the user types
        // backspace then some other character very fast. However, in the case of backspace key
        // repeat, this can lead to flashiness when the cursor flies over positions where the
        // shift state should be updated, so if this is a key repeat, we update after a small delay.
        // Then again, even in the case of a key repeat, if the cursor is at start of text, it
        // can't go any further back, so we can update right away even if it's a key repeat.
        val shiftUpdateKind = if (event.isKeyRepeat && mConnection.expectedSelectionStart > 0)
            InputTransaction.SHIFT_UPDATE_LATER
        else
            InputTransaction.SHIFT_UPDATE_NOW
        inputTransaction.requireShiftUpdate(shiftUpdateKind)

        if (wordComposer.isCursorFrontOrMiddleOfComposingWord) {
            // If we are in the middle of a recorrection, we need to commit the recorrection
            // first so that we can remove the character at the current cursor position.
            // We also need to unlearn the original word that is now being corrected.
            unlearnWord(wordComposer.typedWord, inputTransaction.mSettingsValues,
                    Constants.EVENT_BACKSPACE)
            resetEntireInputState(mConnection.expectedSelectionStart,
                    mConnection.expectedSelectionEnd, true /* clearSuggestionStrip */)
            // When we exit this if-clause, wordComposer.isComposingWord() will return false.
        }
        if (wordComposer.isComposingWord) {
            if (wordComposer.isBatchMode) {
                val rejectedSuggestion = wordComposer.typedWord
                wordComposer.reset()
                wordComposer.rejectedBatchModeSuggestion = rejectedSuggestion
                if (!TextUtils.isEmpty(rejectedSuggestion)) {
                    unlearnWord(rejectedSuggestion, inputTransaction.mSettingsValues,
                            Constants.EVENT_REJECTION)
                }
                StatsUtils.onBackspaceWordDelete(rejectedSuggestion.length)
            } else {
                wordComposer.applyProcessedEvent(event)
                StatsUtils.onBackspacePressed(1)
            }
            if (wordComposer.isComposingWord) {
                setComposingTextInternal(getTextWithUnderline(wordComposer.typedWord), 1)
            } else {
                mConnection.commitText("", 1)
            }
            inputTransaction.setRequiresUpdateSuggestions()
        } else {
            if (lastComposedWord.canRevertCommit()) {
                val lastComposedWord = lastComposedWord.mTypedWord
                revertCommit(inputTransaction, inputTransaction.mSettingsValues)
                StatsUtils.onRevertAutoCorrect()
                StatsUtils.onWordCommitUserTyped(lastComposedWord, wordComposer.isBatchMode)
                // Restart suggestions when backspacing into a reverted word. This is required for
                // the final corrected word to be learned, as learning only occurs when suggestions
                // are active.
                //
                // Note: restartSuggestionsOnWordTouchedByCursor is already called for normal
                // (non-revert) backspace handling.
                if (inputTransaction.mSettingsValues.isSuggestionsEnabledPerUserSettings
                        && inputTransaction.mSettingsValues.mSpacingAndPunctuations
                                .mCurrentLanguageHasSpaces
                        && !mConnection.isCursorFollowedByWordCharacter(
                                inputTransaction.mSettingsValues.mSpacingAndPunctuations)) {
                    restartSuggestionsOnWordTouchedByCursor(inputTransaction.mSettingsValues,
                            false /* forStartInput */, currentKeyboardScriptId)
                }
                return
            }
            if (mEnteredText != null && mConnection.sameAsTextBeforeCursor(mEnteredText!!)) {
                // Cancel multi-character input: remove the text we just entered.
                // This is triggered on backspace after a key that inputs multiple characters,
                // like the smiley key or the .com key.
                mConnection.deleteTextBeforeCursor(mEnteredText!!.length)
                StatsUtils.onDeleteMultiCharInput(mEnteredText!!.length)
                mEnteredText = null
                // If we have mEnteredText, then we know that mHasUncommittedTypedChars == false.
                // In addition we know that spaceState is false, and that we should not be
                // reverting any autocorrect at this point. So we can safely return.
                return
            }
            if (SpaceState.DOUBLE == inputTransaction.mSpaceState) {
                cancelDoubleSpacePeriodCountdown()
                if (mConnection.revertDoubleSpacePeriod(
                                inputTransaction.mSettingsValues.mSpacingAndPunctuations)) {
                    // No need to reset mSpaceState, it has already be done (that's why we
                    // receive it as a parameter)
                    inputTransaction.setRequiresUpdateSuggestions()
                    wordComposer.setCapitalizedModeAtStartComposingTime(
                            WordComposer.CAPS_MODE_OFF)
                    StatsUtils.onRevertDoubleSpacePeriod()
                    return
                }
            } else if (SpaceState.SWAP_PUNCTUATION == inputTransaction.mSpaceState) {
                if (mConnection.revertSwapPunctuation()) {
                    StatsUtils.onRevertSwapPunctuation()
                    // Likewise
                    return
                }
            }

            var hasUnlearnedWordBeingDeleted = false

            // No cancelling of commit/double space/swap: we have a regular backspace.
            // We should backspace one char and restart suggestion if at the end of a word.
            if (mConnection.hasSelection()) {
                // If there is a selection, remove it.
                // We also need to unlearn the selected text.
                val selection = mConnection.getSelectedText(0 /* 0 for no styles */)
                if (!TextUtils.isEmpty(selection)) {
                    unlearnWord(selection!!.toString(), inputTransaction.mSettingsValues,
                            Constants.EVENT_BACKSPACE)
                    hasUnlearnedWordBeingDeleted = true
                }
                val numCharsDeleted = mConnection.expectedSelectionEnd - mConnection.expectedSelectionStart
                mConnection.setSelection(mConnection.expectedSelectionEnd,
                        mConnection.expectedSelectionEnd)
                mConnection.deleteTextBeforeCursor(numCharsDeleted)
                StatsUtils.onBackspaceSelectedText(numCharsDeleted)
            } else {
                // There is no selection, just delete one character.
                if (inputTransaction.mSettingsValues.mInputAttributes.isTypeNull
                        || Constants.NOT_A_CURSOR_POSITION == mConnection.expectedSelectionEnd) {
                    // There are three possible reasons to send a key event: either the field has
                    // type TYPE_NULL, in which case the keyboard should send events, or we are
                    // running in backward compatibility mode, or we don't know the cursor position.
                    // Before Jelly bean, the keyboard would simulate a hardware keyboard event on
                    // pressing enter or delete. This is bad for many reasons (there are race
                    // conditions with commits) but some applications are relying on this behavior
                    // so we continue to support it for older apps, so we retain this behavior if
                    // the app has target SDK < JellyBean.
                    // As for the case where we don't know the cursor position, it can happen
                    // because of bugs in the framework. But the framework should know, so the next
                    // best thing is to leave it to whatever it thinks is best.
                    sendDownUpKeyEvent(KeyEvent.KEYCODE_DEL)
                    var totalDeletedLength = 1
                    if (mDeleteCount > Constants.DELETE_ACCELERATE_AT) {
                        // If this is an accelerated (i.e., double) deletion, then we need to
                        // consider unlearning here because we may have already reached
                        // the previous word, and will lose it after next deletion.
                        hasUnlearnedWordBeingDeleted = hasUnlearnedWordBeingDeleted or unlearnWordBeingDeleted(
                                inputTransaction.mSettingsValues, currentKeyboardScriptId)
                        sendDownUpKeyEvent(KeyEvent.KEYCODE_DEL)
                        totalDeletedLength++
                    }
                    StatsUtils.onBackspacePressed(totalDeletedLength)
                } else {
                    val codePointBeforeCursor = mConnection.codePointBeforeCursor
                    if (codePointBeforeCursor == Constants.NOT_A_CODE) {
                        // HACK for backward compatibility with broken apps that haven't realized
                        // yet that hardware keyboards are not the only way of inputting text.
                        // Nothing to delete before the cursor. We should not do anything, but many
                        // broken apps expect something to happen in this case so that they can
                        // catch it and have their broken interface react. If you need the keyboard
                        // to do this, you're doing it wrong -- please fix your app.
                        mConnection.deleteTextBeforeCursor(1)
                        // TODO: Add a new StatsUtils method onBackspaceWhenNoText()
                        return
                    }
                    val lengthToDelete = if (Character.isSupplementaryCodePoint(codePointBeforeCursor)) 2 else 1
                    mConnection.deleteTextBeforeCursor(lengthToDelete)
                    var totalDeletedLength = lengthToDelete
                    if (mDeleteCount > Constants.DELETE_ACCELERATE_AT) {
                        // If this is an accelerated (i.e., double) deletion, then we need to
                        // consider unlearning here because we may have already reached
                        // the previous word, and will lose it after next deletion.
                        hasUnlearnedWordBeingDeleted = hasUnlearnedWordBeingDeleted or unlearnWordBeingDeleted(
                                inputTransaction.mSettingsValues, currentKeyboardScriptId)
                        val codePointBeforeCursorToDeleteAgain = mConnection.codePointBeforeCursor
                        if (codePointBeforeCursorToDeleteAgain != Constants.NOT_A_CODE) {
                            val lengthToDeleteAgain = if (Character.isSupplementaryCodePoint(
                                            codePointBeforeCursorToDeleteAgain))
                                2
                            else
                                1
                            mConnection.deleteTextBeforeCursor(lengthToDeleteAgain)
                            totalDeletedLength += lengthToDeleteAgain
                        }
                    }
                    StatsUtils.onBackspacePressed(totalDeletedLength)
                }
            }
            if (!hasUnlearnedWordBeingDeleted) {
                // Consider unlearning the word being deleted (if we have not done so already).
                unlearnWordBeingDeleted(
                        inputTransaction.mSettingsValues, currentKeyboardScriptId)
            }
            if (mConnection.hasSlowInputConnection()) {
                mSuggestionStripViewAccessor.setNeutralSuggestionStrip()
            } else if (inputTransaction.mSettingsValues.isSuggestionsEnabledPerUserSettings
                    && inputTransaction.mSettingsValues.mSpacingAndPunctuations
                            .mCurrentLanguageHasSpaces
                    && !mConnection.isCursorFollowedByWordCharacter(
                            inputTransaction.mSettingsValues.mSpacingAndPunctuations)) {
                restartSuggestionsOnWordTouchedByCursor(inputTransaction.mSettingsValues,
                        false /* forStartInput */, currentKeyboardScriptId)
            }
        }
    }

    internal fun getWordAtCursor(settingsValues: SettingsValues, currentKeyboardScriptId: Int): String {
        if (!mConnection.hasSelection()
                && settingsValues.isSuggestionsEnabledPerUserSettings
                && settingsValues.mSpacingAndPunctuations.mCurrentLanguageHasSpaces) {
            val range = mConnection.getWordRangeAtCursor(
                    settingsValues.mSpacingAndPunctuations,
                    currentKeyboardScriptId)
            if (range != null) {
                return range.mWord.toString()
            }
        }
        return ""
    }

    internal fun unlearnWordBeingDeleted(
            settingsValues: SettingsValues, currentKeyboardScriptId: Int): Boolean {
        if (mConnection.hasSlowInputConnection()) {
            // TODO: Refactor unlearning so that it does not incur any extra calls
            // to the InputConnection. That way it can still be performed on a slow
            // InputConnection.
            Log.w(TAG, "Skipping unlearning due to slow InputConnection.")
            return false
        }
        // If we just started backspacing to delete a previous word (but have not
        // entered the composing state yet), unlearn the word.
        // TODO: Consider tracking whether or not this word was typed by the user.
        if (!mConnection.isCursorFollowedByWordCharacter(settingsValues.mSpacingAndPunctuations)) {
            val wordBeingDeleted = getWordAtCursor(
                    settingsValues, currentKeyboardScriptId)
            if (!TextUtils.isEmpty(wordBeingDeleted)) {
                unlearnWord(wordBeingDeleted, settingsValues, Constants.EVENT_BACKSPACE)
                return true
            }
        }
        return false
    }

    internal fun unlearnWord(word: String, settingsValues: SettingsValues, eventType: Int) {
        val ngramContext = mConnection.getNgramContextFromNthPreviousWord(
                settingsValues.mSpacingAndPunctuations, 2)
        val timeStampInSeconds = TimeUnit.MILLISECONDS.toSeconds(
                System.currentTimeMillis())
        mDictionaryFacilitator?.unlearnFromUserHistory(
                word, ngramContext, timeStampInSeconds, eventType)
    }

    /**
     * Handle a press on the language switch key (the "globe key")
     */
    private fun handleLanguageSwitchKey() {
        mLatinIME.switchToNextSubtype()
    }

    /**
     * Swap a space with a space-swapping punctuation sign.
     *
     * This method will check that there are two characters before the cursor and that the first
     * one is a space before it does the actual swapping.
     * @param event The event to handle.
     * @param inputTransaction The transaction in progress.
     * @return true if the swap has been performed, false if it was prevented by preliminary checks.
     */
    private fun trySwapSwapperAndSpace(event: Event,
                                       inputTransaction: InputTransaction): Boolean {
        val codePointBeforeCursor = mConnection.codePointBeforeCursor
        if (Constants.CODE_SPACE != codePointBeforeCursor) {
            return false
        }
        mConnection.deleteTextBeforeCursor(1)
        val text = event.textToCommit.toString() + " "
        mConnection.commitText(text, 1)
        inputTransaction.requireShiftUpdate(InputTransaction.SHIFT_UPDATE_NOW)
        return true
    }

    /*
     * Strip a trailing space if necessary and returns whether it's a swap weak space situation.
     * @param event The event to handle.
     * @param inputTransaction The transaction in progress.
     * @return whether we should swap the space instead of removing it.
     */
    private fun tryStripSpaceAndReturnWhetherShouldSwapInstead(event: Event,
                                                               inputTransaction: InputTransaction): Boolean {
        val codePoint = event.mCodePoint
        val isFromSuggestionStrip = event.isSuggestionStripPress
        if (Constants.CODE_ENTER == codePoint && SpaceState.SWAP_PUNCTUATION == inputTransaction.mSpaceState) {
            mConnection.removeTrailingSpace()
            return false
        }
        if ((SpaceState.WEAK == inputTransaction.mSpaceState || SpaceState.SWAP_PUNCTUATION == inputTransaction.mSpaceState) && isFromSuggestionStrip) {
            if (inputTransaction.mSettingsValues.isUsuallyPrecededBySpace(codePoint)) {
                return false
            }
            if (inputTransaction.mSettingsValues.isUsuallyFollowedBySpace(codePoint)) {
                return true
            }
            mConnection.removeTrailingSpace()
        }
        return false
    }

    fun startDoubleSpacePeriodCountdown(inputTransaction: InputTransaction) {
        mDoubleSpacePeriodCountdownStart = inputTransaction.mTimestamp
    }

    fun cancelDoubleSpacePeriodCountdown() {
        mDoubleSpacePeriodCountdownStart = 0
    }

    fun isDoubleSpacePeriodCountdownActive(inputTransaction: InputTransaction): Boolean {
        return inputTransaction.mTimestamp - mDoubleSpacePeriodCountdownStart < inputTransaction.mSettingsValues.mDoubleSpacePeriodTimeout
    }

    /**
     * Apply the double-space-to-period transformation if applicable.
     *
     * The double-space-to-period transformation means that we replace two spaces with a
     * period-space sequence of characters. This typically happens when the user presses space
     * twice in a row quickly.
     * This method will check that the double-space-to-period is active in settings, that the
     * two spaces have been input close enough together, that the typed character is a space
     * and that the previous character allows for the transformation to take place. If all of
     * these conditions are fulfilled, this method applies the transformation and returns true.
     * Otherwise, it does nothing and returns false.
     *
     * @param event The event to handle.
     * @param inputTransaction The transaction in progress.
     * @return true if we applied the double-space-to-period transformation, false otherwise.
     */
    private fun tryPerformDoubleSpacePeriod(event: Event,
                                            inputTransaction: InputTransaction): Boolean {
        // Check the setting, the typed character and the countdown. If any of the conditions is
        // not fulfilled, return false.
        if (!inputTransaction.mSettingsValues.mUseDoubleSpacePeriod
                || Constants.CODE_SPACE != event.mCodePoint
                || !isDoubleSpacePeriodCountdownActive(inputTransaction)) {
            return false
        }
        // We only do this when we see one space and an accepted code point before the cursor.
        // The code point may be a surrogate pair but the space may not, so we need 3 chars.
        val lastTwo = mConnection.getTextBeforeCursor(3, 0) ?: return false
        val length = lastTwo.length
        if (length < 2) return false
        if (lastTwo[length - 1].toInt() != Constants.CODE_SPACE) {
            return false
        }
        // We know there is a space in pos -1, and we have at least two chars. If we have only two
        // chars, isSurrogatePairs can't return true as charAt(1) is a space, so this is fine.
        val firstCodePoint = if (Character.isSurrogatePair(lastTwo[0], lastTwo[1]))
            Character.codePointAt(lastTwo, length - 3)
        else
            lastTwo[length - 2].toInt()
        if (canBeFollowedByDoubleSpacePeriod(firstCodePoint)) {
            cancelDoubleSpacePeriodCountdown()
            mConnection.deleteTextBeforeCursor(1)
            val textToInsert = inputTransaction.mSettingsValues.mSpacingAndPunctuations
                    .mSentenceSeparatorAndSpace
            mConnection.commitText(textToInsert, 1)
            inputTransaction.requireShiftUpdate(InputTransaction.SHIFT_UPDATE_NOW)
            inputTransaction.setRequiresUpdateSuggestions()
            return true
        }
        return false
    }

    /**
     * Performs a recapitalization event.
     * @param settingsValues The current settings values.
     */
    private fun performRecapitalization(settingsValues: SettingsValues) {
        if (!mConnection.hasSelection() || !mRecapitalizeStatus.mIsEnabled()) {
            return  // No selection or recapitalize is disabled for now
        }
        val selectionStart = mConnection.expectedSelectionStart
        val selectionEnd = mConnection.expectedSelectionEnd
        val numCharsSelected = selectionEnd - selectionStart
        if (numCharsSelected > Constants.MAX_CHARACTERS_FOR_RECAPITALIZATION) {
            // We bail out if we have too many characters for performance reasons. We don't want
            // to suck possibly multiple-megabyte data.
            return
        }
        // If we have a recapitalize in progress, use it; otherwise, start a new one.
        if (!mRecapitalizeStatus.isStarted || !mRecapitalizeStatus.isSetAt(selectionStart, selectionEnd)) {
            val selectedText = mConnection.getSelectedText(0 /* flags, 0 for no styles */)
            if (TextUtils.isEmpty(selectedText)) return  // Race condition with the input connection
            mRecapitalizeStatus.start(selectionStart, selectionEnd, selectedText!!.toString(),
                    settingsValues.mLocale,
                    settingsValues.mSpacingAndPunctuations.mSortedWordSeparators)
            // We trim leading and trailing whitespace.
            mRecapitalizeStatus.trim()
        }
        mConnection.finishComposingText()
        mRecapitalizeStatus.rotate()
        mConnection.setSelection(selectionEnd, selectionEnd)
        mConnection.deleteTextBeforeCursor(numCharsSelected)
        mConnection.commitText(mRecapitalizeStatus.recapitalizedString, 0)
        mConnection.setSelection(mRecapitalizeStatus.newCursorStart,
                mRecapitalizeStatus.newCursorEnd)
    }

    private fun performAdditionToUserHistoryDictionary(settingsValues: SettingsValues,
                                                       suggestion: String, ngramContext: NgramContext) {
        // If correction is not enabled, we don't add words to the user history dictionary.
        // That's to avoid unintended additions in some sensitive fields, or fields that
        // expect to receive non-words.
        if (!settingsValues.mAutoCorrectionEnabledPerUserSettings) return
        if (mConnection.hasSlowInputConnection()) {
            // Since we don't unlearn when the user backspaces on a slow InputConnection,
            // turn off learning to guard against adding typos that the user later deletes.
            Log.w(TAG, "Skipping learning due to slow InputConnection.")
            return
        }

        if (TextUtils.isEmpty(suggestion)) return
        val wasAutoCapitalized = wordComposer.wasAutoCapitalized() && !wordComposer.isMostlyCaps
        val timeStampInSeconds = TimeUnit.MILLISECONDS.toSeconds(
                System.currentTimeMillis()).toInt()
        mDictionaryFacilitator?.addToUserHistory(suggestion, wasAutoCapitalized,
                ngramContext, timeStampInSeconds.toLong(), settingsValues.mBlockPotentiallyOffensive)
    }

    private var suggestionStripDisposable: Disposable? = null

    fun performUpdateSuggestionStripSync(settingsValues: SettingsValues,
                                         inputStyle: Int) {
        // Check if we have a suggestion engine attached.
        if (!settingsValues.needsToLookupSuggestions()) {
            if (wordComposer.isComposingWord) {
                Log.w(TAG, "Called updateSuggestionsOrPredictions but suggestions were not " + "requested!")
            }
            // Clear the suggestions strip.
            mSuggestionStripViewAccessor.showSuggestionStrip(SuggestedWords.emptyInstance)
            return
        }

        if (!wordComposer.isComposingWord && !settingsValues.mBigramPredictionEnabled) {
            mSuggestionStripViewAccessor.setNeutralSuggestionStrip()
            return
        }

        val s = Single.create<SuggestedWords> { emitter ->
            mInputLogicHandler.getSuggestedWords(inputStyle, SuggestedWords.NOT_A_SEQUENCE_NUMBER) { suggestedWords ->
                Log.d(TAG, suggestedWords.size().toString())
                val typedWordString = wordComposer.typedWord
                val typedWordInfo = SuggestedWordInfo(
                        typedWordString, "" /* prevWordsContext */,
                        SuggestedWordInfo.MAX_SCORE,
                        SuggestedWordInfo.KIND_TYPED,
                        Dictionary.DICTIONARY_USER_TYPED,
                        SuggestedWordInfo.NOT_AN_INDEX /* indexOfTouchPointOfSecondWord */,
                        SuggestedWordInfo.NOT_A_CONFIDENCE)
                // Show new suggestions if we have at least one. Otherwise keep the old
                // suggestions with the new typed word. Exception: if the length of the
                // typed word is <= 1 (after a deletion typically) we clear old suggestions.
                if (suggestedWords.size() > 1 || typedWordString.length <= 1) {
                    emitter.onSuccess(suggestedWords)
                } else {
                    emitter.onSuccess(retrieveOlderSuggestions(typedWordInfo, mSuggestedWords))
                }
            }
        }

        suggestionStripDisposable = s
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ suggestedWords ->
                    mSuggestionStripViewAccessor.showSuggestionStrip(suggestedWords)
                }, {
                    Log.wtf(TAG, it)
                    Sentry.capture(it)
                })
    }

    /**
     * Check if the cursor is touching a word. If so, restart suggestions on this word, else
     * do nothing.
     *
     * @param settingsValues the current values of the settings.
     * @param forStartInput whether we're doing this in answer to starting the input (as opposed
     * to a cursor move, for example). In ICS, there is a platform bug that we need to work
     * around only when we come here at input start time.
     */
    fun restartSuggestionsOnWordTouchedByCursor(settingsValues: SettingsValues,
                                                forStartInput: Boolean,
            // TODO: remove this argument, put it into settingsValues
                                                currentKeyboardScriptId: Int) {
        Log.d("InputLogic", "restartSuggestionsOnWordTouchedByCursor")
        // HACK: We may want to special-case some apps that exhibit bad behavior in case of
        // recorrection. This is a temporary, stopgap measure that will be removed later.
        // TODO: remove this.
        // Recorrection is not supported in languages without spaces because we don't know
        // how to segment them yet.
        if (!settingsValues.mSpacingAndPunctuations.mCurrentLanguageHasSpaces
                // If no suggestions are requested, don't try restarting suggestions.
                || !settingsValues.needsToLookupSuggestions()
                // If we are currently in a batch input, we must not resume suggestions, or the result
                // of the batch input will replace the new composition. This may happen in the corner case
                // that the app moves the cursor on its own accord during a batch input.
                || mInputLogicHandler.isInBatchInput
                // If the cursor is not touching a word, or if there is a selection, return right away.
                || mConnection.hasSelection()
                // If we don't know the cursor location, return.
                || mConnection.expectedSelectionStart < 0) {
            mSuggestionStripViewAccessor.setNeutralSuggestionStrip()
            return
        }
        val expectedCursorPosition = mConnection.expectedSelectionStart
        if (!/* checkTextAfter */mConnection.isCursorTouchingWord(settingsValues.mSpacingAndPunctuations,
                        true)) {
            // Show predictions.
            wordComposer.setCapitalizedModeAtStartComposingTime(WordComposer.CAPS_MODE_OFF)
            mLatinIME.mHandler.postUpdateSuggestionStrip(SuggestedWords.INPUT_STYLE_RECORRECTION)
            return
        }
        val range = mConnection.getWordRangeAtCursor(
                settingsValues.mSpacingAndPunctuations, currentKeyboardScriptId) ?: return
// Happens if we don't have an input connection at all
        if (range.length() <= 0) {
            // Race condition, or touching a word in a non-supported script.
            mLatinIME.setNeutralSuggestionStrip()
            return
        }
        // If for some strange reason (editor bug or so) we measure the text before the cursor as
        // longer than what the entire text is supposed to be, the safe thing to do is bail out.
        if (range.mHasUrlSpans) return  // If there are links, we don't resume suggestions. Making
        // edits to a linkified text through batch commands would ruin the URL spans, and unless
        // we take very complicated steps to preserve the whole link, we can't do things right so
        // we just do not resume because it's safer.
        val numberOfCharsInWordBeforeCursor = range.numberOfCharsInWordBeforeCursor
        if (numberOfCharsInWordBeforeCursor > expectedCursorPosition) return
        val suggestions = ArrayList<SuggestedWordInfo>()
        val typedWordString = range.mWord.toString()
        val typedWordInfo = SuggestedWordInfo(typedWordString,
                "" /* prevWordsContext */, SuggestedWords.MAX_SUGGESTIONS + 1,
                SuggestedWordInfo.KIND_TYPED, Dictionary.DICTIONARY_USER_TYPED,
                SuggestedWordInfo.NOT_AN_INDEX /* indexOfTouchPointOfSecondWord */,
                SuggestedWordInfo.NOT_A_CONFIDENCE /* autoCommitFirstWordConfidence */)
        suggestions.add(typedWordInfo)
        if (!isResumableWord(settingsValues, typedWordString)) {
            mSuggestionStripViewAccessor.setNeutralSuggestionStrip()
            return
        }
        var i = 0
        for (span in range.suggestionSpansAtWord) {
            for (s in span.suggestions) {
                ++i
                if (!TextUtils.equals(s, typedWordString)) {
                    suggestions.add(SuggestedWordInfo(s,
                            "" /* prevWordsContext */, SuggestedWords.MAX_SUGGESTIONS - i,
                            SuggestedWordInfo.KIND_RESUMED, Dictionary.DICTIONARY_RESUMED,
                            SuggestedWordInfo.NOT_AN_INDEX /* indexOfTouchPointOfSecondWord */,
                            SuggestedWordInfo.NOT_A_CONFIDENCE
                            /* autoCommitFirstWordConfidence */))
                }
            }
        }
        val codePoints = StringUtils.toCodePointArray(typedWordString)
        wordComposer.setComposingWord(codePoints,
                mLatinIME.getCoordinatesForCurrentKeyboard(codePoints))
        wordComposer.setCursorPositionWithinWord(
                typedWordString.codePointCount(0, numberOfCharsInWordBeforeCursor))
        if (forStartInput) {
            mConnection.maybeMoveTheCursorAroundAndRestoreToWorkaroundABug()
        }
        mConnection.setComposingRegion(expectedCursorPosition - numberOfCharsInWordBeforeCursor,
                expectedCursorPosition + range.numberOfCharsInWordAfterCursor)
        if (suggestions.size <= 1) {
            // If there weren't any suggestion spans on this word, suggestions#size() will be 1
            // if shouldIncludeResumedWordInSuggestions is true, 0 otherwise. In this case, we
            // have no useful suggestions, so we will try to compute some for it instead.
            mInputLogicHandler.getSuggestedWords(
                    Suggest.SESSION_ID_TYPING,
                    SuggestedWords.NOT_A_SEQUENCE_NUMBER)
            { suggestedWords ->
                doShowSuggestionsAndClearAutoCorrectionIndicator(suggestedWords)
            }
        } else {
            // We found suggestion spans in the word. We'll create the SuggestedWords out of
            // them, and make willAutoCorrect false. We make typedWordValid false, because the
            // color of the word in the suggestion strip changes according to this parameter,
            // and false gives the correct color.
            val suggestedWords = SuggestedWords(suggestions, null, typedWordInfo, false /* typedWordValid */,
                    false /* willAutoCorrect */, false /* isObsoleteSuggestions */,
                    SuggestedWords.INPUT_STYLE_RECORRECTION, SuggestedWords.NOT_A_SEQUENCE_NUMBER)/* rawSuggestions */
            doShowSuggestionsAndClearAutoCorrectionIndicator(suggestedWords)
        }
    }

    internal fun doShowSuggestionsAndClearAutoCorrectionIndicator(suggestedWords: SuggestedWords) {
        mIsAutoCorrectionIndicatorOn = false
        mLatinIME.mHandler.showSuggestionStrip(suggestedWords)
    }

    /**
     * Reverts a previous commit with auto-correction.
     *
     * This is triggered upon pressing backspace just after a commit with auto-correction.
     *
     * @param inputTransaction The transaction in progress.
     * @param settingsValues the current values of the settings.
     */
    private fun revertCommit(inputTransaction: InputTransaction,
                             settingsValues: SettingsValues) {
        val originallyTypedWord = lastComposedWord.mTypedWord
        val originallyTypedWordString = originallyTypedWord?.toString() ?: ""
        val committedWord = lastComposedWord.mCommittedWord
        val committedWordString = committedWord.toString()
        val cancelLength = committedWord.length
        val separatorString = lastComposedWord.mSeparatorString
        // If our separator is a space, we won't actually commit it,
        // but set the space state to PHANTOM so that a space will be inserted
        // on the next keypress
        val usePhantomSpace = separatorString == Constants.STRING_SPACE
        // We want java chars, not codepoints for the following.
        val separatorLength = separatorString.length
        // TODO: should we check our saved separator against the actual contents of the text view?
        val deleteLength = cancelLength + separatorLength
        if (DebugFlags.DEBUG_ENABLED) {
            if (wordComposer.isComposingWord) {
                throw RuntimeException("revertCommit, but we are composing a word")
            }
            val wordBeforeCursor = mConnection.getTextBeforeCursor(deleteLength, 0).subSequence(0, cancelLength)
            if (!TextUtils.equals(committedWord, wordBeforeCursor)) {
                throw RuntimeException("revertCommit check failed: we thought we were "
                        + "reverting \"" + committedWord
                        + "\", but before the cursor we found \"" + wordBeforeCursor + "\"")
            }
        }
        mConnection.deleteTextBeforeCursor(deleteLength)
        if (!TextUtils.isEmpty(committedWord)) {
            unlearnWord(committedWordString, inputTransaction.mSettingsValues,
                    Constants.EVENT_REVERT)
        }
        val stringToCommit = originallyTypedWord!! + if (usePhantomSpace) "" else separatorString
        val textToCommit = SpannableString(stringToCommit)
        if (committedWord is SpannableString) {
            val spans = committedWord.getSpans(0,
                    committedWord.length, Any::class.java)
            val lastCharIndex = textToCommit.length - 1
            // We will collect all suggestions in the following array.
            val suggestions = ArrayList<String>()
            // First, add the committed word to the list of suggestions.
            suggestions.add(committedWordString)
            for (span in spans) {
                // If this is a suggestion span, we check that the word is not the committed word.
                // That should mostly be the case.
                // Given this, we add it to the list of suggestions, otherwise we discard it.
                if (span is SuggestionSpan) {
                    for (suggestion in span.suggestions) {
                        if (suggestion != committedWordString) {
                            suggestions.add(suggestion)
                        }
                    }
                } else {
                    // If this is not a suggestion span, we just add it as is.
                    textToCommit.setSpan(span, 0 /* start */, lastCharIndex /* end */,
                            committedWord.getSpanFlags(span))
                }
            }
            // Add the suggestion list to the list of suggestions.
            textToCommit.setSpan(SuggestionSpan(mLatinIME /* context */,
                    inputTransaction.mSettingsValues.mLocale,
                    suggestions.toTypedArray(), 0 /* flags */, null/* notificationTargetClass */),
                    0 /* start */, lastCharIndex /* end */, 0 /* flags */)
        }

        if (inputTransaction.mSettingsValues.mSpacingAndPunctuations.mCurrentLanguageHasSpaces) {
            mConnection.commitText(textToCommit, 1)
            if (usePhantomSpace) {
                mSpaceState = SpaceState.PHANTOM
            }
        } else {
            // For languages without spaces, we revert the typed string but the cursor is flush
            // with the typed word, so we need to resume suggestions right away.
            val codePoints = StringUtils.toCodePointArray(stringToCommit)
            wordComposer.setComposingWord(codePoints,
                    mLatinIME.getCoordinatesForCurrentKeyboard(codePoints))
            setComposingTextInternal(textToCommit, 1)
        }
        // Don't restart suggestion yet. We'll restart if the user deletes the separator.
        lastComposedWord = LastComposedWord.NOT_A_COMPOSED_WORD

        // We have a separator between the word and the cursor: we should show predictions.
        inputTransaction.setRequiresUpdateSuggestions()
    }

    /**
     * Factor in auto-caps and manual caps and compute the current caps mode.
     * @param settingsValues the current settings values.
     * @param keyboardShiftMode the current shift mode of the keyboard. See
     * KeyboardSwitcher#getKeyboardShiftMode() for possible values.
     * @return the actual caps mode the keyboard is in right now.
     */
    private fun getActualCapsMode(settingsValues: SettingsValues,
                                  keyboardShiftMode: Int): Int {
        if (keyboardShiftMode != WordComposer.CAPS_MODE_AUTO_SHIFTED) {
            return keyboardShiftMode
        }
        val auto = getCurrentAutoCapsState(settingsValues)
        if (0 != auto and TextUtils.CAP_MODE_CHARACTERS) {
            return WordComposer.CAPS_MODE_AUTO_SHIFT_LOCKED
        }
        return if (0 != auto) {
            WordComposer.CAPS_MODE_AUTO_SHIFTED
        } else WordComposer.CAPS_MODE_OFF
    }

    /**
     * Gets the current auto-caps state, factoring in the space state.
     *
     * This method tries its best to do this in the most efficient possible manner. It avoids
     * getting text from the editor if possible at all.
     * This is called from the KeyboardSwitcher (through a trampoline in LatinIME) because it
     * needs to know auto caps state to display the right layout.
     *
     * @param settingsValues the relevant settings values
     * @return a caps mode from TextUtils.CAP_MODE_* or Constants.TextUtils.CAP_MODE_OFF.
     */
    fun getCurrentAutoCapsState(settingsValues: SettingsValues): Int {
        if (!settingsValues.mAutoCap) return Constants.TextUtils.CAP_MODE_OFF

        val ei = currentInputEditorInfo ?: return Constants.TextUtils.CAP_MODE_OFF
        val inputType = ei.inputType
        // Warning: this depends on mSpaceState, which may not be the most current value. If
        // mSpaceState gets updated later, whoever called this may need to be told about it.
        return mConnection.getCursorCapsMode(inputType, settingsValues.mSpacingAndPunctuations,
                SpaceState.PHANTOM == mSpaceState)
    }

    /**
     * Get n-gram context from the nth previous word before the cursor as context
     * for the suggestion process.
     * @param spacingAndPunctuations the current spacing and punctuations settings.
     * @param nthPreviousWord reverse index of the word to get (1-indexed)
     * @return the information of previous words
     */
    private fun getNgramContextFromNthPreviousWordForSuggestion(
            spacingAndPunctuations: SpacingAndPunctuations, nthPreviousWord: Int): NgramContext {
        if (spacingAndPunctuations.mCurrentLanguageHasSpaces) {
            // If we are typing in a language with spaces we can just look up the previous
            // word information from textview.
            return mConnection.getNgramContextFromNthPreviousWord(
                    spacingAndPunctuations, nthPreviousWord)
        }
        return if (LastComposedWord.NOT_A_COMPOSED_WORD == lastComposedWord) {
            NgramContext.BEGINNING_OF_SENTENCE
        } else NgramContext(NgramContext.WordInfo(
                lastComposedWord.mCommittedWord.toString()))
    }

    /**
     * @param actionId the action to perform
     */
    private fun performEditorAction(actionId: Int) {
        mConnection.performEditorAction(actionId)
    }

    /**
     * Perform the processing specific to inputting TLDs.
     *
     * Some keys input a TLD (specifically, the ".com" key) and this warrants some specific
     * processing. First, if this is a TLD, we ignore PHANTOM spaces -- this is done by type
     * of character in onCodeInput, but since this gets inputted as a whole string we need to
     * do it here specifically. Then, if the last character before the cursor is a period, then
     * we cut the dot at the start of ".com". This is because humans tend to type "www.google."
     * and then press the ".com" key and instinctively don't expect to get "www.google..com".
     *
     * @param text the raw text supplied to onTextInput
     * @return the text to actually send to the editor
     */
    private fun performSpecificTldProcessingOnTextInput(text: String): String {
        if (text.length <= 1 || text[0].toInt() != Constants.CODE_PERIOD
                || !Character.isLetter(text[1])) {
            // Not a tld: do nothing.
            return text
        }
        // We have a TLD (or something that looks like this): make sure we don't add
        // a space even if currently in phantom mode.
        mSpaceState = SpaceState.NONE
        val codePointBeforeCursor = mConnection.codePointBeforeCursor
        // If no code point, #getCodePointBeforeCursor returns NOT_A_CODE_POINT.
        return if (Constants.CODE_PERIOD == codePointBeforeCursor) {
            text.substring(1)
        } else text
    }

    /**
     * Handle a press on the settings key.
     */
    private fun onSettingsKeyPressed() {
        mLatinIME.displaySettingsDialog()
    }

    /**
     * Resets the whole input state to the starting state.
     *
     * This will clear the composing word, reset the last composed word, clear the suggestion
     * strip and tell the input connection about it so that it can refresh its caches.
     *
     * @param newSelStart the new selection start, in java characters.
     * @param newSelEnd the new selection end, in java characters.
     * @param clearSuggestionStrip whether this method should clear the suggestion strip.
     */
    // TODO: how is this different from startInput ?!
    private fun resetEntireInputState(newSelStart: Int, newSelEnd: Int,
                                      clearSuggestionStrip: Boolean) {
        val shouldFinishComposition = wordComposer.isComposingWord
        resetComposingState(true /* alsoResetLastComposedWord */)
        if (clearSuggestionStrip) {
            mSuggestionStripViewAccessor.setNeutralSuggestionStrip()
        }
        mConnection.resetCachesUponCursorMoveAndReturnSuccess(newSelStart, newSelEnd,
                shouldFinishComposition)
    }

    /**
     * Resets only the composing state.
     *
     * Compare #resetEntireInputState, which also clears the suggestion strip and resets the
     * input connection caches. This only deals with the composing state.
     *
     * @param alsoResetLastComposedWord whether to also reset the last composed word.
     */
    private fun resetComposingState(alsoResetLastComposedWord: Boolean) {
        wordComposer.reset()
        if (alsoResetLastComposedWord) {
            lastComposedWord = LastComposedWord.NOT_A_COMPOSED_WORD
        }
    }

    /**
     * Gets a chunk of text with or the auto-correction indicator underline span as appropriate.
     *
     * This method looks at the old state of the auto-correction indicator to put or not put
     * the underline span as appropriate. It is important to note that this does not correspond
     * exactly to whether this word will be auto-corrected to or not: what's important here is
     * to keep the same indication as before.
     * When we add a new code point to a composing word, we don't know yet if we are going to
     * auto-correct it until the suggestions are computed. But in the mean time, we still need
     * to display the character and to extend the previous underline. To avoid any flickering,
     * the underline should keep the same color it used to have, even if that's not ultimately
     * the correct color for this new word. When the suggestions are finished evaluating, we
     * will call this method again to fix the color of the underline.
     *
     * @param text the text on which to maybe apply the span.
     * @return the same text, with the auto-correction underline span if that's appropriate.
     */
    // TODO: Shouldn't this go in some *Utils class instead?
    private fun getTextWithUnderline(text: String): CharSequence? {
        // TODO: Locale should be determined based on context and the text given.
        return if (mIsAutoCorrectionIndicatorOn)
            SuggestionSpanUtils.getTextWithAutoCorrectionIndicatorUnderline(
                    mLatinIME, text, dictionaryFacilitatorLocale)
        else
            text
    }

    /**
     * Sends a DOWN key event followed by an UP key event to the editor.
     *
     * If possible at all, avoid using this method. It causes all sorts of race conditions with
     * the text view because it goes through a different, asynchronous binder. Also, batch edits
     * are ignored for key events. Use the normal software input methods instead.
     *
     * @param keyCode the key code to send inside the key event.
     */
    private fun sendDownUpKeyEvent(keyCode: Int) {
        val eventTime = SystemClock.uptimeMillis()
        mConnection.sendKeyEvent(KeyEvent(eventTime, eventTime,
                KeyEvent.ACTION_DOWN, keyCode, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE))
        mConnection.sendKeyEvent(KeyEvent(SystemClock.uptimeMillis(), eventTime,
                KeyEvent.ACTION_UP, keyCode, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE))
    }

    /**
     * Sends a code point to the editor, using the most appropriate method.
     *
     * Normally we send code points with commitText, but there are some cases (where backward
     * compatibility is a concern for example) where we want to use deprecated methods.
     *
     * @param settingsValues the current values of the settings.
     * @param codePoint the code point to send.
     */
    // TODO: replace these two parameters with an InputTransaction
    private fun sendKeyCodePoint(settingsValues: SettingsValues, codePoint: Int) {
        // TODO: Remove this special handling of digit letters.
        // For backward compatibility. See {@link InputMethodService#sendKeyChar(char)}.
        if (codePoint >= '0'.toInt() && codePoint <= '9'.toInt()) {
            sendDownUpKeyEvent(codePoint - '0'.toInt() + KeyEvent.KEYCODE_0)
            return
        }

        // TODO: we should do this also when the editor has TYPE_NULL
        mConnection.commitText(StringUtils.newSingleCodePointString(codePoint), 1)
    }

    /**
     * Insert an automatic space, if the options allow it.
     *
     * This checks the options and the text before the cursor are appropriate before inserting
     * an automatic space.
     *
     * @param settingsValues the current values of the settings.
     */
    private fun insertAutomaticSpaceIfOptionsAndTextAllow(settingsValues: SettingsValues) {
        if (settingsValues.shouldInsertSpacesAutomatically()
                && settingsValues.mSpacingAndPunctuations.mCurrentLanguageHasSpaces
                && !mConnection.textBeforeCursorLooksLikeURL()) {
            sendKeyCodePoint(settingsValues, Constants.CODE_SPACE)
        }
    }

    /**
     * Do the final processing after a batch input has ended. This commits the word to the editor.
     * @param settingsValues the current values of the settings.
     * @param suggestedWords suggestedWords to use.
     */
    fun onUpdateTailBatchInputCompleted(settingsValues: SettingsValues,
                                        suggestedWords: SuggestedWords, keyboardSwitcher: KeyboardSwitcher) {
        val batchInputText = if (suggestedWords.isEmpty) null else suggestedWords.getWord(0)
        if (TextUtils.isEmpty(batchInputText)) {
            return
        }
        mConnection.beginBatchEdit()
        if (SpaceState.PHANTOM == mSpaceState) {
            insertAutomaticSpaceIfOptionsAndTextAllow(settingsValues)
        }
        wordComposer.setBatchInputWord(batchInputText!!)
        setComposingTextInternal(batchInputText, 1)
        mConnection.endBatchEdit()
        // Space state must be updated before calling updateShiftState
        mSpaceState = SpaceState.PHANTOM
        keyboardSwitcher.requestUpdatingShiftState(getCurrentAutoCapsState(settingsValues),
                currentRecapitalizeState)
    }

    /**
     * Commit the typed string to the editor.
     *
     * This is typically called when we should commit the currently composing word without applying
     * auto-correction to it. Typically, we come here upon pressing a separator when the keyboard
     * is configured to not do auto-correction at all (because of the settings or the properties of
     * the editor). In this case, `separatorString' is set to the separator that was pressed.
     * We also come here in a variety of cases with external user action. For example, when the
     * cursor is moved while there is a composition, or when the keyboard is closed, or when the
     * user presses the Send button for an SMS, we don't auto-correct as that would be unexpected.
     * In this case, `separatorString' is set to NOT_A_SEPARATOR.
     *
     * @param settingsValues the current values of the settings.
     * @param separatorString the separator that's causing the commit, or NOT_A_SEPARATOR if none.
     */
    fun commitTyped(settingsValues: SettingsValues, separatorString: String) {
        if (!wordComposer.isComposingWord) return
        val typedWord = wordComposer.typedWord
        if (typedWord.length > 0) {
            val isBatchMode = wordComposer.isBatchMode
            commitChosenWord(settingsValues, typedWord,
                    LastComposedWord.COMMIT_TYPE_USER_TYPED_WORD, separatorString)
            StatsUtils.onWordCommitUserTyped(typedWord, isBatchMode)
        }
    }

    /**
     * Commit the current auto-correction.
     *
     * This will commit the best guess of the keyboard regarding what the user meant by typing
     * the currently composing word. The IME computes suggestions and assigns a confidence score
     * to each of them; when it's confident enough in one suggestion, it replaces the typed string
     * by this suggestion at commit time. When it's not confident enough, or when it has no
     * suggestions, or when the settings or environment does not allow for auto-correction, then
     * this method just commits the typed string.
     * Note that if suggestions are currently being computed in the background, this method will
     * block until the computation returns. This is necessary for consistency (it would be very
     * strange if pressing space would commit a different word depending on how fast you press).
     *
     * @param settingsValues the current value of the settings.
     * @param separator the separator that's causing the commit to happen.
     */
    private fun commitCurrentAutoCorrection(settingsValues: SettingsValues,
                                            separator: String, handler: LatinIME.UIHandler) {
        // Complete any pending suggestions query first
        if (handler.hasPendingUpdateSuggestions()) {
            handler.cancelUpdateSuggestionStrip()
            // To know the input style here, we should retrieve the in-flight "update suggestions"
            // message and read its arg1 member here. However, the Handler class does not let
            // us retrieve this message, so we can't do that. But in fact, we notice that
            // we only ever come here when the input style was typing. In the case of batch
            // input, we update the suggestions synchronously when the tail batch comes. Likewise
            // for application-specified completions. As for recorrections, we never auto-correct,
            // so we don't come here either. Hence, the input style is necessarily
            // INPUT_STYLE_TYPING.
            performUpdateSuggestionStripSync(settingsValues, SuggestedWords.INPUT_STYLE_TYPING)
        }
        val autoCorrectionOrNull = wordComposer.autoCorrectionOrNull
        val typedWord = wordComposer.typedWord
        val stringToCommit = if (autoCorrectionOrNull != null)
            autoCorrectionOrNull.word
        else
            typedWord
        if (stringToCommit != null) {
            if (TextUtils.isEmpty(typedWord)) {
                throw RuntimeException("We have an auto-correction but the typed word " + "is empty? Impossible! I must commit suicide.")
            }
            val isBatchMode = wordComposer.isBatchMode
            commitChosenWord(settingsValues, stringToCommit,
                    LastComposedWord.COMMIT_TYPE_DECIDED_WORD, separator)
            if (typedWord != stringToCommit) {
                // This will make the correction flash for a short while as a visual clue
                // to the user that auto-correction happened. It has no other effect; in particular
                // note that this won't affect the text inside the text field AT ALL: it only makes
                // the segment of text starting at the supplied index and running for the length
                // of the auto-correction flash. At this moment, the "typedWord" argument is
                // ignored by TextView.
                mConnection.commitCorrection(CorrectionInfo(
                        mConnection.expectedSelectionEnd - stringToCommit.length,
                        typedWord, stringToCommit))
                val prevWordsContext = if (autoCorrectionOrNull != null)
                    autoCorrectionOrNull.mPrevWordsContext
                else
                    ""
                StatsUtils.onAutoCorrection(typedWord, stringToCommit, isBatchMode,
                        mDictionaryFacilitator, prevWordsContext)
                StatsUtils.onWordCommitAutoCorrect(stringToCommit, isBatchMode)
            } else {
                StatsUtils.onWordCommitUserTyped(stringToCommit, isBatchMode)
            }
        }
    }

    /**
     * Commits the chosen word to the text field and saves it for later retrieval.
     *
     * @param settingsValues the current values of the settings.
     * @param chosenWord the word we want to commit.
     * @param commitType the type of the commit, as one of LastComposedWord.COMMIT_TYPE_*
     * @param separatorString the separator that's causing the commit, or NOT_A_SEPARATOR if none.
     */
    private fun commitChosenWord(settingsValues: SettingsValues, chosenWord: String,
                                 commitType: Int, separatorString: String) {
        var startTimeMillis: Long = 0
        if (DebugFlags.DEBUG_ENABLED) {
            startTimeMillis = System.currentTimeMillis()
            Log.d(TAG, "commitChosenWord() : [$chosenWord]")
        }
        val suggestedWords = mSuggestedWords
        // TODO: Locale should be determined based on context and the text given.
        val locale = dictionaryFacilitatorLocale
// b/21926256
        //      SuggestionSpanUtils.getTextWithSuggestionSpan(mLatinIME, chosenWord,
        //                suggestedWords, locale);
        if (DebugFlags.DEBUG_ENABLED) {
            val runTimeMillis = System.currentTimeMillis() - startTimeMillis
            Log.d(TAG, "commitChosenWord() : " + runTimeMillis + " ms to run "
                    + "SuggestionSpanUtils.getTextWithSuggestionSpan()")
            startTimeMillis = System.currentTimeMillis()
        }
        // When we are composing word, get n-gram context from the 2nd previous word because the
        // 1st previous word is the word to be committed. Otherwise get n-gram context from the 1st
        // previous word.
        val ngramContext = mConnection.getNgramContextFromNthPreviousWord(
                settingsValues.mSpacingAndPunctuations, if (wordComposer.isComposingWord) 2 else 1)
        if (DebugFlags.DEBUG_ENABLED) {
            val runTimeMillis = System.currentTimeMillis() - startTimeMillis
            Log.d(TAG, "commitChosenWord() : " + runTimeMillis + " ms to run "
                    + "Connection.getNgramContextFromNthPreviousWord()")
            Log.d(TAG, "commitChosenWord() : NgramContext = $ngramContext")
            startTimeMillis = System.currentTimeMillis()
        }
        mConnection.commitText(chosenWord, 1)
        if (DebugFlags.DEBUG_ENABLED) {
            val runTimeMillis = System.currentTimeMillis() - startTimeMillis
            Log.d(TAG, "commitChosenWord() : " + runTimeMillis + " ms to run "
                    + "Connection.commitText")
            startTimeMillis = System.currentTimeMillis()
        }
        // Add the word to the user history dictionary
        performAdditionToUserHistoryDictionary(settingsValues, chosenWord, ngramContext)
        if (DebugFlags.DEBUG_ENABLED) {
            val runTimeMillis = System.currentTimeMillis() - startTimeMillis
            Log.d(TAG, "commitChosenWord() : " + runTimeMillis + " ms to run "
                    + "performAdditionToUserHistoryDictionary()")
            startTimeMillis = System.currentTimeMillis()
        }
        // TODO: figure out here if this is an auto-correct or if the best word is actually
        // what user typed. Note: currently this is done much later in
        // LastComposedWord#didCommitTypedWord by string equality of the remembered
        // strings.
        lastComposedWord = wordComposer.commitWord(commitType,
                chosenWord, separatorString, ngramContext)
        if (DebugFlags.DEBUG_ENABLED) {
            val runTimeMillis = System.currentTimeMillis() - startTimeMillis
            Log.d(TAG, "commitChosenWord() : " + runTimeMillis + " ms to run "
                    + "WordComposer.commitWord()")
            startTimeMillis = System.currentTimeMillis()
        }
    }

    /**
     * Retry resetting caches in the rich input connection.
     *
     * When the editor can't be accessed we can't reset the caches, so we schedule a retry.
     * This method handles the retry, and re-schedules a new retry if we still can't access.
     * We only retry up to 5 times before giving up.
     *
     * @param tryResumeSuggestions Whether we should resume suggestions or not.
     * @param remainingTries How many times we may try again before giving up.
     * @return whether true if the caches were successfully reset, false otherwise.
     */
    fun retryResetCachesAndReturnSuccess(tryResumeSuggestions: Boolean,
                                         remainingTries: Int, handler: LatinIME.UIHandler): Boolean {
        val shouldFinishComposition = mConnection.hasSelection() || !mConnection.isCursorPositionKnown
        if (!mConnection.resetCachesUponCursorMoveAndReturnSuccess(
                        mConnection.expectedSelectionStart, mConnection.expectedSelectionEnd,
                        shouldFinishComposition)) {
            if (0 < remainingTries) {
                handler.postResetCaches(tryResumeSuggestions, remainingTries - 1)
                return false
            }
            // If remainingTries is 0, we should stop waiting for new tries, however we'll still
            // return true as we need to perform other tasks (for example, loading the keyboard).
        }
        mConnection.tryFixLyingCursorPosition()
        if (tryResumeSuggestions) {
            handler.postResumeSuggestions(true /* shouldDelay */)
        }
        return true
    }

    fun getSuggestedWords(settingsValues: SettingsValues,
                          keyboard: Keyboard, keyboardShiftMode: Int, inputStyle: Int,
                          sequenceNumber: Int, callback: OnGetSuggestedWordsCallback) {
        wordComposer.adviseCapitalizedModeBeforeFetchingSuggestions(
                getActualCapsMode(settingsValues, keyboardShiftMode))
        suggest.getSuggestedWords(wordComposer,
                getNgramContextFromNthPreviousWordForSuggestion(
                        settingsValues.mSpacingAndPunctuations,
                        // Get the word on which we should search the bigrams. If we are composing
                        // a word, it's whatever is *before* the half-committed word in the buffer,
                        // hence 2; if we aren't, we should just skip whitespace if any, so 1.
                        if (wordComposer.isComposingWord) 2 else 1),
                keyboard,
                SettingsValuesForSuggestion(settingsValues.mBlockPotentiallyOffensive),
                settingsValues.mAutoCorrectionEnabledPerUserSettings,
                inputStyle, sequenceNumber, callback)
    }

    /**
     * Used as an injection point for each call of
     * [RichInputConnection.setComposingText].
     *
     *
     * Currently using this method is optional and you can still directly call
     * [RichInputConnection.setComposingText], but it is recommended to
     * use this method whenever possible.
     *
     *
     *
     * TODO: Should we move this mechanism to [RichInputConnection]?
     *
     * @param newComposingText the composing text to be set
     * @param newCursorPosition the new cursor position
     */
    private fun setComposingTextInternal(newComposingText: CharSequence?,
                                         newCursorPosition: Int) {
        setComposingTextInternalWithBackgroundColor(newComposingText, newCursorPosition,
                Color.TRANSPARENT, newComposingText!!.length)
    }

    /**
     * Equivalent to [.setComposingTextInternal] except that this method
     * allows to set [BackgroundColorSpan] to the composing text with the given color.
     *
     *
     * TODO: Currently the background color is exclusive with the black underline, which is
     * automatically added by the framework. We need to change the framework if we need to have both
     * of them at the same time.
     *
     * TODO: Should we move this method to [RichInputConnection]?
     *
     * @param newComposingText the composing text to be set
     * @param newCursorPosition the new cursor position
     * @param backgroundColor the background color to be set to the composing text. Set
     * [Color.TRANSPARENT] to disable the background color.
     * @param coloredTextLength the length of text, in Java chars, which should be rendered with
     * the given background color.
     */
    private fun setComposingTextInternalWithBackgroundColor(newComposingText: CharSequence?,
                                                            newCursorPosition: Int, backgroundColor: Int, coloredTextLength: Int) {
        val composingTextToBeSet: CharSequence?
        if (backgroundColor == Color.TRANSPARENT) {
            composingTextToBeSet = newComposingText
        } else {
            val spannable = SpannableString(newComposingText)
            val backgroundColorSpan = BackgroundColorSpan(backgroundColor)
            val spanLength = Math.min(coloredTextLength, spannable.length)
            spannable.setSpan(backgroundColorSpan, 0, spanLength,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE or Spanned.SPAN_COMPOSING)
            composingTextToBeSet = spannable
        }
        mConnection.setComposingText(composingTextToBeSet, newCursorPosition)
    }

    companion object {
        private val TAG = InputLogic::class.java.simpleName

        /**
         * Returns whether this code point can be followed by the double-space-to-period transformation.
         *
         * See #maybeDoubleSpaceToPeriod for details.
         * Generally, most word characters can be followed by the double-space-to-period transformation,
         * while most punctuation can't. Some punctuation however does allow for this to take place
         * after them, like the closing parenthesis for example.
         *
         * @param codePoint the code point after which we may want to apply the transformation
         * @return whether it's fine to apply the transformation after this code point.
         */
        private fun canBeFollowedByDoubleSpacePeriod(codePoint: Int): Boolean {
            // TODO: This should probably be a blacklist rather than a whitelist.
            // TODO: This should probably be language-dependant...
            return (Character.isLetterOrDigit(codePoint)
                    || codePoint == Constants.CODE_SINGLE_QUOTE
                    || codePoint == Constants.CODE_DOUBLE_QUOTE
                    || codePoint == Constants.CODE_CLOSING_PARENTHESIS
                    || codePoint == Constants.CODE_CLOSING_SQUARE_BRACKET
                    || codePoint == Constants.CODE_CLOSING_CURLY_BRACKET
                    || codePoint == Constants.CODE_CLOSING_ANGLE_BRACKET
                    || codePoint == Constants.CODE_PLUS
                    || codePoint == Constants.CODE_PERCENT
                    || Character.getType(codePoint) == Character.OTHER_SYMBOL.toInt())
        }

        /**
         * Tests the passed word for resumability.
         *
         * We can resume suggestions on words whose first code point is a word code point (with some
         * nuances: check the code for details).
         *
         * @param settings the current values of the settings.
         * @param word the word to evaluate.
         * @return whether it's fine to resume suggestions on this word.
         */
        private fun isResumableWord(settings: SettingsValues, word: String): Boolean {
            val firstCodePoint = word.codePointAt(0)
            return (settings.isWordCodePoint(firstCodePoint)
                    && Constants.CODE_SINGLE_QUOTE != firstCodePoint
                    && Constants.CODE_DASH != firstCodePoint)
        }

        /**
         * Make a [com.android.inputmethod.latin.SuggestedWords] object containing a typed word
         * and obsolete suggestions.
         * See [com.android.inputmethod.latin.SuggestedWords.getTypedWordAndPreviousSuggestions].
         * @param typedWordInfo The typed word as a SuggestedWordInfo.
         * @param previousSuggestedWords The previously suggested words.
         * @return Obsolete suggestions with the newly typed word.
         */
        internal fun retrieveOlderSuggestions(typedWordInfo: SuggestedWordInfo,
                                              previousSuggestedWords: SuggestedWords): SuggestedWords {
            val oldSuggestedWords = if (previousSuggestedWords.isPunctuationSuggestions)
                SuggestedWords.emptyInstance
            else
                previousSuggestedWords
            val typedWordAndPreviousSuggestions = SuggestedWords.getTypedWordAndPreviousSuggestions(typedWordInfo, oldSuggestedWords)
            return SuggestedWords(typedWordAndPreviousSuggestions, null,
                    typedWordInfo, false /* typedWordValid */, false /* hasAutoCorrectionCandidate */,
                    true /* isObsoleteSuggestions */, oldSuggestedWords.mInputStyle,
                    SuggestedWords.NOT_A_SEQUENCE_NUMBER)/* rawSuggestions */
        }
    }
}
