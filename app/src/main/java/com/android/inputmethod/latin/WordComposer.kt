/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.inputmethod.latin

import com.android.inputmethod.annotations.UsedForTesting
import com.android.inputmethod.event.CombinerChain
import com.android.inputmethod.event.Event
import com.android.inputmethod.latin.SuggestedWords.SuggestedWordInfo
import com.android.inputmethod.latin.common.ComposedData
import com.android.inputmethod.latin.common.Constants
import com.android.inputmethod.latin.common.CoordinateUtils
import com.android.inputmethod.latin.common.InputPointers
import com.android.inputmethod.latin.common.StringUtils
import com.android.inputmethod.latin.define.DebugFlags
import com.android.inputmethod.latin.define.DecoderSpecificConstants

import java.util.ArrayList
import java.util.Collections

/**
 * A place to store the currently composing word with information such as adjacent key codes as well
 */
class WordComposer {

    private var mCombinerChain: CombinerChain? = null
    private var mCombiningSpec: String? = null // Memory so that we don't uselessly recreate the combiner chain

    // The list of events that served to compose this string.
    private val mEvents: ArrayList<Event>
    val inputPointers = InputPointers(MAX_WORD_LENGTH)
    /**
     * @return the auto-correction for this word, or null if none.
     */
    var autoCorrectionOrNull: SuggestedWordInfo? = null
        private set
    /**
     * @return whether we started composing this word by resuming suggestion on an existing string
     */
    var isResumed: Boolean = false
        private set
    var isBatchMode: Boolean = false
        private set
    // A memory of the last rejected batch mode suggestion, if any. This goes like this: the user
    // gestures a word, is displeased with the results and hits backspace, then gestures again.
    // At the very least we should avoid re-suggesting the same thing, and to do that we memorize
    // the rejected suggestion in this variable.
    // TODO: this should be done in a comprehensive way by the User History feature instead of
    // as an ad-hockery here.
    var rejectedBatchModeSuggestion: String? = null

    // Cache these values for performance
    private var mTypedWordCache: CharSequence? = null
    private var mCapsCount: Int = 0
    private var mDigitsCount: Int = 0
    private var mCapitalizedMode: Int = 0
    // This is the number of code points entered so far. This is not limited to MAX_WORD_LENGTH.
    // In general, this contains the size of mPrimaryKeyCodes, except when this is greater than
    // MAX_WORD_LENGTH in which case mPrimaryKeyCodes only contain the first MAX_WORD_LENGTH
    // code points.
    private var mCodePointSize: Int = 0
    private var mCursorPositionWithinWord: Int = 0

    /**
     * Whether the composing word has the only first char capitalized.
     */
    private var mIsOnlyFirstCharCapitalized: Boolean = false

    val composedDataSnapshot: ComposedData
        get() = ComposedData(inputPointers, isBatchMode, mTypedWordCache!!.toString())

    val isSingleLetter: Boolean
        get() = size() == 1

    val isComposingWord: Boolean
        get() = size() > 0

    val isCursorFrontOrMiddleOfComposingWord: Boolean
        get() {
            if (DBG && mCursorPositionWithinWord > mCodePointSize) {
                throw RuntimeException("Wrong cursor position : " + mCursorPositionWithinWord
                        + "in a word of size " + mCodePointSize)
            }
            return mCursorPositionWithinWord != mCodePointSize
        }

    /**
     * Returns the word as it was typed, without any correction applied.
     * @return the word that was typed so far. Never returns null.
     */
    val typedWord: String
        get() = mTypedWordCache!!.toString()

    /**
     * Whether this composer is composing or about to compose a word in which only the first letter
     * is a capital.
     *
     * If we do have a composing word, we just return whether the word has indeed only its first
     * character capitalized. If we don't, then we return a value based on the capitalized mode,
     * which tell us what is likely to happen for the next composing word.
     *
     * @return capitalization preference
     */
    val isOrWillBeOnlyFirstCharCapitalized: Boolean
        get() = if (isComposingWord)
            mIsOnlyFirstCharCapitalized
        else
            CAPS_MODE_OFF != mCapitalizedMode

    /**
     * Whether or not all of the user typed chars are upper case
     * @return true if all user typed chars are upper case, false otherwise
     */
    val isAllUpperCase: Boolean
        get() = if (size() <= 1) {
            mCapitalizedMode == CAPS_MODE_AUTO_SHIFT_LOCKED || mCapitalizedMode == CAPS_MODE_MANUAL_SHIFT_LOCKED
        } else mCapsCount == size()

    /**
     * Returns true if more than one character is upper case, otherwise returns false.
     */
    val isMostlyCaps: Boolean
        get() = mCapsCount > 1

    init {
        mCombinerChain = CombinerChain("")
        mEvents = ArrayList()
        autoCorrectionOrNull = null
        isResumed = false
        isBatchMode = false
        mCursorPositionWithinWord = 0
        rejectedBatchModeSuggestion = null
        refreshTypedWordCache()
    }

    /**
     * Restart the combiners, possibly with a new spec.
     * @param combiningSpec The spec string for combining. This is found in the extra value.
     */
    fun restartCombining(combiningSpec: String?) {
        val nonNullCombiningSpec = combiningSpec ?: ""
        if (nonNullCombiningSpec != mCombiningSpec) {
            mCombinerChain = CombinerChain(
                    mCombinerChain!!.composingWordWithCombiningFeedback.toString())
            mCombiningSpec = nonNullCombiningSpec
        }
    }

    /**
     * Clear out the keys registered so far.
     */
    fun reset() {
        mCombinerChain!!.reset()
        mEvents.clear()
        autoCorrectionOrNull = null
        mCapsCount = 0
        mDigitsCount = 0
        mIsOnlyFirstCharCapitalized = false
        isResumed = false
        isBatchMode = false
        mCursorPositionWithinWord = 0
        rejectedBatchModeSuggestion = null
        refreshTypedWordCache()
    }

    private fun refreshTypedWordCache() {
        mTypedWordCache = mCombinerChain!!.composingWordWithCombiningFeedback
        mCodePointSize = Character.codePointCount(mTypedWordCache!!, 0, mTypedWordCache!!.length)
    }

    /**
     * Number of keystrokes in the composing word.
     * @return the number of keystrokes
     */
    fun size(): Int {
        return mCodePointSize
    }

    /**
     * Process an event and return an event, and return a processed event to apply.
     * @param event the unprocessed event.
     * @return the processed event. Never null, but may be marked as consumed.
     */
    fun processEvent(event: Event): Event {
        val processedEvent = mCombinerChain!!.processEvent(mEvents, event)
        // The retained state of the combiner chain may have changed while processing the event,
        // so we need to update our cache.
        refreshTypedWordCache()
        mEvents.add(event)
        return processedEvent
    }

    /**
     * Apply a processed input event.
     *
     * All input events should be supported, including software/hardware events, characters as well
     * as deletions, multiple inputs and gestures.
     *
     * @param event the event to apply. Must not be null.
     */
    fun applyProcessedEvent(event: Event) {
        mCombinerChain!!.applyProcessedEvent(event)
        val primaryCode = event.mCodePoint
        val keyX = event.mX
        val keyY = event.mY
        val newIndex = size()
        refreshTypedWordCache()
        mCursorPositionWithinWord = mCodePointSize
        // We may have deleted the last one.
        if (0 == mCodePointSize) {
            mIsOnlyFirstCharCapitalized = false
        }
        if (Constants.CODE_DELETE != event.mKeyCode) {
            if (newIndex < MAX_WORD_LENGTH) {
                // In the batch input mode, the {@code mInputPointers} holds batch input points and
                // shouldn't be overridden by the "typed key" coordinates
                // (See {@link #setBatchInputWord}).
                if (!isBatchMode) {
                    // TODO: Set correct pointer id and time
                    inputPointers.addPointerAt(newIndex, keyX, keyY, 0, 0)
                }
            }
            if (0 == newIndex) {
                mIsOnlyFirstCharCapitalized = Character.isUpperCase(primaryCode)
            } else {
                mIsOnlyFirstCharCapitalized = mIsOnlyFirstCharCapitalized && !Character.isUpperCase(primaryCode)
            }
            if (Character.isUpperCase(primaryCode)) mCapsCount++
            if (Character.isDigit(primaryCode)) mDigitsCount++
        }
        autoCorrectionOrNull = null
    }

    fun setCursorPositionWithinWord(posWithinWord: Int) {
        mCursorPositionWithinWord = posWithinWord
        // TODO: compute where that puts us inside the events
    }

    /**
     * When the cursor is moved by the user, we need to update its position.
     * If it falls inside the currently composing word, we don't reset the composition, and
     * only update the cursor position.
     *
     * @param expectedMoveAmount How many java chars to move the cursor. Negative values move
     * the cursor backward, positive values move the cursor forward.
     * @return true if the cursor is still inside the composing word, false otherwise.
     */
    fun moveCursorByAndReturnIfInsideComposingWord(expectedMoveAmount: Int): Boolean {
        var actualMoveAmount = 0
        var cursorPos = mCursorPositionWithinWord
        // TODO: Don't make that copy. We can do this directly from mTypedWordCache.
        val codePoints = StringUtils.toCodePointArray(mTypedWordCache!!)
        if (expectedMoveAmount >= 0) {
            // Moving the cursor forward for the expected amount or until the end of the word has
            // been reached, whichever comes first.
            while (actualMoveAmount < expectedMoveAmount && cursorPos < codePoints.size) {
                actualMoveAmount += Character.charCount(codePoints[cursorPos])
                ++cursorPos
            }
        } else {
            // Moving the cursor backward for the expected amount or until the start of the word
            // has been reached, whichever comes first.
            while (actualMoveAmount > expectedMoveAmount && cursorPos > 0) {
                --cursorPos
                actualMoveAmount -= Character.charCount(codePoints[cursorPos])
            }
        }
        // If the actual and expected amounts differ, we crossed the start or the end of the word
        // so the result would not be inside the composing word.
        if (actualMoveAmount != expectedMoveAmount) {
            return false
        }
        mCursorPositionWithinWord = cursorPos
        mCombinerChain!!.applyProcessedEvent(mCombinerChain!!.processEvent(
                mEvents, Event.createCursorMovedEvent(cursorPos)))
        return true
    }

    fun setBatchInputPointers(batchPointers: InputPointers) {
        inputPointers.set(batchPointers)
        isBatchMode = true
    }

    fun setBatchInputWord(word: String) {
        reset()
        isBatchMode = true
        val length = word.length
        var i = 0
        while (i < length) {
            val codePoint = Character.codePointAt(word, i)
            // We don't want to override the batch input points that are held in mInputPointers
            // (See {@link #add(int,int,int)}).
            val processedEvent = processEvent(Event.createEventForCodePointFromUnknownSource(codePoint))
            applyProcessedEvent(processedEvent)
            i = Character.offsetByCodePoints(word, i, 1)
        }
    }

    /**
     * Set the currently composing word to the one passed as an argument.
     * This will register NOT_A_COORDINATE for X and Ys, and use the passed keyboard for proximity.
     * @param codePoints the code points to set as the composing word.
     * @param coordinates the x, y coordinates of the key in the CoordinateUtils format
     */
    fun setComposingWord(codePoints: IntArray, coordinates: IntArray) {
        reset()
        val length = codePoints.size
        for (i in 0 until length) {
            val processedEvent = processEvent(Event.createEventForCodePointFromAlreadyTypedText(codePoints[i],
                    CoordinateUtils.xFromArray(coordinates, i),
                    CoordinateUtils.yFromArray(coordinates, i)))
            applyProcessedEvent(processedEvent)
        }
        isResumed = true
    }

    fun wasShiftedNoLock(): Boolean {
        return mCapitalizedMode == CAPS_MODE_AUTO_SHIFTED || mCapitalizedMode == CAPS_MODE_MANUAL_SHIFTED
    }

    /**
     * Returns true if we have digits in the composing word.
     */
    fun hasDigits(): Boolean {
        return mDigitsCount > 0
    }

    /**
     * Saves the caps mode at the start of composing.
     *
     * WordComposer needs to know about the caps mode for several reasons. The first is, we need
     * to know after the fact what the reason was, to register the correct form into the user
     * history dictionary: if the word was automatically capitalized, we should insert it in
     * all-lower case but if it's a manual pressing of shift, then it should be inserted as is.
     * Also, batch input needs to know about the current caps mode to display correctly
     * capitalized suggestions.
     * @param mode the mode at the time of start
     */
    fun setCapitalizedModeAtStartComposingTime(mode: Int) {
        mCapitalizedMode = mode
    }

    /**
     * Before fetching suggestions, we don't necessarily know about the capitalized mode yet.
     *
     * If we don't have a composing word yet, we take a note of this mode so that we can then
     * supply this information to the suggestion process. If we have a composing word, then
     * the previous mode has priority over this.
     * @param mode the mode just before fetching suggestions
     */
    fun adviseCapitalizedModeBeforeFetchingSuggestions(mode: Int) {
        if (!isComposingWord) {
            mCapitalizedMode = mode
        }
    }

    /**
     * Returns whether the word was automatically capitalized.
     * @return whether the word was automatically capitalized
     */
    fun wasAutoCapitalized(): Boolean {
        return mCapitalizedMode == CAPS_MODE_AUTO_SHIFT_LOCKED || mCapitalizedMode == CAPS_MODE_AUTO_SHIFTED
    }

    /**
     * Sets the auto-correction for this word.
     */
    fun setAutoCorrection(autoCorrection: SuggestedWordInfo?) {
        autoCorrectionOrNull = autoCorrection
    }

    // `type' should be one of the LastComposedWord.COMMIT_TYPE_* constants above.
    // committedWord should contain suggestion spans if applicable.
    fun commitWord(type: Int, committedWord: CharSequence,
                   separatorString: String, ngramContext: NgramContext): LastComposedWord {
        // Note: currently, we come here whenever we commit a word. If it's a MANUAL_PICK
        // or a DECIDED_WORD we may cancel the commit later; otherwise, we should deactivate
        // the last composed word to ensure this does not happen.
        val lastComposedWord = LastComposedWord(mEvents,
                inputPointers, mTypedWordCache!!.toString(), committedWord, separatorString,
                ngramContext, mCapitalizedMode)
        inputPointers.reset()
        if (type != LastComposedWord.COMMIT_TYPE_DECIDED_WORD && type != LastComposedWord.COMMIT_TYPE_MANUAL_PICK) {
            lastComposedWord.deactivate()
        }
        mCapsCount = 0
        mDigitsCount = 0
        isBatchMode = false
        mCombinerChain!!.reset()
        mEvents.clear()
        mCodePointSize = 0
        mIsOnlyFirstCharCapitalized = false
        mCapitalizedMode = CAPS_MODE_OFF
        refreshTypedWordCache()
        autoCorrectionOrNull = null
        mCursorPositionWithinWord = 0
        isResumed = false
        rejectedBatchModeSuggestion = null
        return lastComposedWord
    }

    fun resumeSuggestionOnLastComposedWord(lastComposedWord: LastComposedWord) {
        mEvents.clear()
        Collections.copy(mEvents, lastComposedWord.mEvents)
        inputPointers.set(lastComposedWord.mInputPointers)
        mCombinerChain!!.reset()
        refreshTypedWordCache()
        mCapitalizedMode = lastComposedWord.mCapitalizedMode
        autoCorrectionOrNull = null // This will be filled by the next call to updateSuggestion.
        mCursorPositionWithinWord = mCodePointSize
        rejectedBatchModeSuggestion = null
        isResumed = true
    }

    @UsedForTesting
    internal fun addInputPointerForTest(index: Int, keyX: Int, keyY: Int) {
        inputPointers.addPointerAt(index, keyX, keyY, 0, 0)
    }

    @UsedForTesting
    internal fun setTypedWordCacheForTests(typedWordCacheForTests: String) {
        mTypedWordCache = typedWordCacheForTests
    }

    companion object {
        private val MAX_WORD_LENGTH = DecoderSpecificConstants.DICTIONARY_MAX_WORD_LENGTH
        private val DBG = DebugFlags.DEBUG_ENABLED

        val CAPS_MODE_OFF = 0
        // 1 is shift bit, 2 is caps bit, 4 is auto bit but this is just a convention as these bits
        // aren't used anywhere in the code
        val CAPS_MODE_MANUAL_SHIFTED = 0x1
        val CAPS_MODE_MANUAL_SHIFT_LOCKED = 0x3
        val CAPS_MODE_AUTO_SHIFTED = 0x5
        val CAPS_MODE_AUTO_SHIFT_LOCKED = 0x7
    }
}
