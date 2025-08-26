/*
 * Copyright (C) 2008 The Android Open Source Project
 * Copyright (C) 2015 Brendan Molloy <brendan@bbqsrc.net>
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

import android.Manifest.permission
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface.OnClickListener
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.os.Debug
import android.os.IBinder
import android.os.Message
import android.preference.PreferenceManager
import android.text.InputType
import android.util.Log
import android.util.PrintWriterPrinter
import android.util.SparseArray
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup.LayoutParams
import android.view.WindowManager
import android.view.inputmethod.CompletionInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodSubtype
import androidx.annotation.RequiresApi
import androidx.core.view.WindowCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.android.inputmethod.accessibility.AccessibilityUtils
import com.android.inputmethod.annotations.UsedForTesting
import com.android.inputmethod.compat.EditorInfoCompatUtils
import com.android.inputmethod.compat.ViewOutlineProviderCompatUtils
import com.android.inputmethod.event.*
import com.android.inputmethod.keyboard.KeyboardActionListener
import com.android.inputmethod.keyboard.KeyboardId
import com.android.inputmethod.keyboard.KeyboardSwitcher
import com.android.inputmethod.latin.Suggest.OnGetSuggestedWordsCallback
import com.android.inputmethod.latin.SuggestedWords.SuggestedWordInfo
import com.android.inputmethod.latin.common.Constants
import com.android.inputmethod.latin.common.Constants.ImeOption.*
import com.android.inputmethod.latin.common.CoordinateUtils
import com.android.inputmethod.latin.common.InputPointers
import com.android.inputmethod.latin.define.DebugFlags
import com.android.inputmethod.latin.define.ProductionFlags
import com.android.inputmethod.latin.inputlogic.InputLogic
import com.android.inputmethod.latin.permissions.PermissionsManager
import com.android.inputmethod.ui.personaldictionary.PersonalDictionaryActivity
import com.android.inputmethod.latin.settings.Settings
import com.android.inputmethod.latin.settings.SettingsActivity
import com.android.inputmethod.latin.suggestions.SuggestionStripView
import com.android.inputmethod.latin.suggestions.SuggestionStripViewAccessor
import com.android.inputmethod.latin.touchinputconsumer.GestureConsumer
import com.android.inputmethod.latin.utils.*
import no.divvun.dictionary.DivvunDictionaryFacilitator
import no.divvun.domain.loadKeyboardDescriptor
import no.divvun.pahkat.UpdateWorker
import no.divvun.pahkat.hasSubtypesChanged
import no.divvun.pahkat.restartUpdaterIfSubtypesChanged
import timber.log.Timber
import java.io.FileDescriptor
import java.io.PrintWriter
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Input method implementation for Qwerty'ish keyboard.
 */
class LatinIME : InputMethodService(), KeyboardActionListener, SuggestionStripView.Listener, SuggestionStripViewAccessor, DictionaryFacilitator.DictionaryInitializationListener, PermissionsManager.PermissionsResultCallback {

    internal val mSettings: Settings
    private val mDictionaryFacilitator: DictionaryFacilitator = DivvunDictionaryFacilitator()
    internal lateinit var mInputLogic: InputLogic
    // We expect to have only one decoder in almost all cases, hence the default capacity of 1.
    // If it turns out we need several, it will get grown seamlessly.
    internal val mHardwareEventDecoders = SparseArray<HardwareEventDecoder>(1)

    // TODO: Move these {@link View}s to {@link KeyboardSwitcher}.
    private var mInputView: View? = null
    private var mInsetsUpdater: ViewOutlineProviderCompatUtils.InsetsUpdater? = null
    private var mSuggestionStripView: SuggestionStripView? = null
    private var mSpacerView: View? = null

    private var mRichImm: RichInputMethodManager? = null
    @UsedForTesting
    internal val mKeyboardSwitcher: KeyboardSwitcher
    private val mSubtypeState = SubtypeState()
    private var mEmojiAltPhysicalKeyDetector: EmojiAltPhysicalKeyDetector? = null
    private val mStatsUtilsManager: StatsUtilsManager
    // Working variable for {@link #startShowingInputView()} and
    // {@link #onEvaluateInputViewShown()}.
    private var mIsExecutingStartShowingInputView: Boolean = false

    private val mDictionaryDumpBroadcastReceiver = DictionaryDumpBroadcastReceiver(this)

    private var mOptionsDialog: AlertDialog? = null

    private val mIsHardwareAcceleratedDrawingEnabled: Boolean

    private var mGestureConsumer = GestureConsumer.NULL_GESTURE_CONSUMER

    val mHandler = UIHandler(this)

    private val isImeSuppressedByHardwareKeyboard: Boolean
        get() {
            // Bypass hardware keyboard detection in debug builds
            if (BuildConfig.DEBUG) {
                return false
            }
            
            val switcher = KeyboardSwitcher.instance
            return !onEvaluateInputViewShown() && switcher.isImeSuppressedByHardwareKeyboard(
                    mSettings.current, switcher.keyboardSwitchState)
        }

    internal val currentAutoCapsState: Int
        get() = mInputLogic.getCurrentAutoCapsState(mSettings.current)

    internal val currentRecapitalizeState: Int
        get() = mInputLogic.currentRecapitalizeState

    private val isShowingOptionDialog: Boolean
        get() = mOptionsDialog != null && mOptionsDialog!!.isShowing

    // onKeyDown and onKeyUp are the main events we are interested in. There are two more events
    // related to handling of hardware key events that we may want to implement in the future:
    // boolean onKeyLongPress(final int keyCode, final KeyEvent event);
    // boolean onKeyMultiple(final int keyCode, final int count, final KeyEvent event);

    // receive ringer mode change.
    private val mRingerModeChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (action == AudioManager.RINGER_MODE_CHANGED_ACTION) {
                AudioAndHapticFeedbackManager.instance.onRingerModeChanged()
            }
        }
    }

    class UIHandler(ownerInstance: LatinIME) : LeakGuardHandlerWrapper<LatinIME>(ownerInstance) {

        private var mDelayInMillisecondsToUpdateSuggestions: Int = 0
        private var mDelayInMillisecondsToUpdateShiftState: Int = 0

        // Working variables for the following methods.
        private var mIsOrientationChanging: Boolean = false
        private var mPendingSuccessiveImsCallback: Boolean = false
        private var mHasPendingStartInput: Boolean = false
        private var mHasPendingFinishInputView: Boolean = false
        private var mHasPendingFinishInput: Boolean = false
        private var mAppliedEditorInfo: EditorInfo? = null

        fun onCreate() {
            Timber.d("onCreate")
            val latinIme = ownerInstance ?: return
            val res = latinIme.resources
            mDelayInMillisecondsToUpdateSuggestions = res.getInteger(
                    R.integer.config_delay_in_milliseconds_to_update_suggestions)
            mDelayInMillisecondsToUpdateShiftState = res.getInteger(
                    R.integer.config_delay_in_milliseconds_to_update_shift_state)
        }

        override fun handleMessage(msg: Message) {
            val latinIme = ownerInstance ?: return
            val switcher = latinIme.mKeyboardSwitcher
            when (msg.what) {
                MSG_UPDATE_SUGGESTION_STRIP -> {
                    cancelUpdateSuggestionStrip()
                    latinIme.mInputLogic.performUpdateSuggestionStripSync(
                            latinIme.mSettings.current, msg.arg1 /* inputStyle */)
                }
                MSG_UPDATE_SHIFT_STATE -> switcher.requestUpdatingShiftState(latinIme.currentAutoCapsState,
                        latinIme.currentRecapitalizeState)
                MSG_SHOW_GESTURE_PREVIEW_AND_SUGGESTION_STRIP -> if (msg.arg1 == ARG1_NOT_GESTURE_INPUT) {
                    val suggestedWords = msg.obj as SuggestedWords
                    latinIme.showSuggestionStrip(suggestedWords)
                } else {
                    latinIme.showGesturePreviewAndSuggestionStrip(msg.obj as SuggestedWords,
                            msg.arg1 == ARG1_DISMISS_GESTURE_FLOATING_PREVIEW_TEXT)
                }
                MSG_RESUME_SUGGESTIONS -> latinIme.mInputLogic.restartSuggestionsOnWordTouchedByCursor(
                        latinIme.mSettings.current, false /* forStartInput */,
                        latinIme.mKeyboardSwitcher.currentKeyboardScriptId)
                MSG_RESUME_SUGGESTIONS_FOR_START_INPUT -> latinIme.mInputLogic.restartSuggestionsOnWordTouchedByCursor(
                        latinIme.mSettings.current, true /* forStartInput */,
                        latinIme.mKeyboardSwitcher.currentKeyboardScriptId)
                MSG_REOPEN_DICTIONARIES -> {
                    // We need to re-evaluate the currently composing word in case the script has
                    // changed.
                    postWaitForDictionaryLoad()
                    latinIme.resetDictionaryFacilitatorIfNecessary()
                }
                MSG_UPDATE_TAIL_BATCH_INPUT_COMPLETED -> {
                    val suggestedWords = msg.obj as SuggestedWords
                    latinIme.mInputLogic.onUpdateTailBatchInputCompleted(
                            latinIme.mSettings.current,
                            suggestedWords, latinIme.mKeyboardSwitcher)
                    latinIme.onTailBatchInputResultShown(suggestedWords)
                }
                MSG_RESET_CACHES -> {
                    val settingsValues = latinIme.mSettings.current
                    if (latinIme.mInputLogic.retryResetCachesAndReturnSuccess(
                                    msg.arg1 == ARG1_TRUE /* tryResumeSuggestions */,
                                    msg.arg2 /* remainingTries */, this /* handler */)) {
                        // If we were able to reset the caches, then we can reload the keyboard.
                        // Otherwise, we'll do it when we can.
                        latinIme.mKeyboardSwitcher.loadKeyboard(latinIme.currentInputEditorInfo,
                                settingsValues, latinIme.currentAutoCapsState,
                                latinIme.currentRecapitalizeState)
                    }
                }
                MSG_WAIT_FOR_DICTIONARY_LOAD -> Log.i(TAG, "Timeout waiting for dictionary load")
                MSG_DEALLOCATE_MEMORY -> latinIme.deallocateMemory()
                MSG_SWITCH_LANGUAGE_AUTOMATICALLY -> latinIme.switchLanguage(msg.obj as InputMethodSubtype)
            }
        }

        fun postUpdateSuggestionStrip(inputStyle: Int) {
            sendMessageDelayed(obtainMessage(MSG_UPDATE_SUGGESTION_STRIP, inputStyle,
                    0 /* ignored */), mDelayInMillisecondsToUpdateSuggestions.toLong())
        }

        fun postReopenDictionaries() {
            sendMessage(obtainMessage(MSG_REOPEN_DICTIONARIES))
        }

        private fun postResumeSuggestionsInternal(shouldDelay: Boolean,
                                                  forStartInput: Boolean) {
            val latinIme = ownerInstance ?: return
            if (!latinIme.mSettings.current.isSuggestionsEnabledPerUserSettings) {
                return
            }
            removeMessages(MSG_RESUME_SUGGESTIONS)
            removeMessages(MSG_RESUME_SUGGESTIONS_FOR_START_INPUT)
            val message = if (forStartInput)
                MSG_RESUME_SUGGESTIONS_FOR_START_INPUT
            else
                MSG_RESUME_SUGGESTIONS
            if (shouldDelay) {
                sendMessageDelayed(obtainMessage(message),
                        mDelayInMillisecondsToUpdateSuggestions.toLong())
            } else {
                sendMessage(obtainMessage(message))
            }
        }

        fun postResumeSuggestions(shouldDelay: Boolean) {
            postResumeSuggestionsInternal(shouldDelay, false /* forStartInput */)
        }

        fun postResumeSuggestionsForStartInput(shouldDelay: Boolean) {
            postResumeSuggestionsInternal(shouldDelay, true /* forStartInput */)
        }

        fun postResetCaches(tryResumeSuggestions: Boolean, remainingTries: Int) {
            removeMessages(MSG_RESET_CACHES)
            sendMessage(obtainMessage(MSG_RESET_CACHES, if (tryResumeSuggestions) 1 else 0,
                    remainingTries, null))
        }

        fun postWaitForDictionaryLoad() {
            sendMessageDelayed(obtainMessage(MSG_WAIT_FOR_DICTIONARY_LOAD),
                    DELAY_WAIT_FOR_DICTIONARY_LOAD_MILLIS)
        }

        fun cancelWaitForDictionaryLoad() {
            removeMessages(MSG_WAIT_FOR_DICTIONARY_LOAD)
        }

        fun hasPendingWaitForDictionaryLoad(): Boolean {
            return hasMessages(MSG_WAIT_FOR_DICTIONARY_LOAD)
        }

        fun cancelUpdateSuggestionStrip() {
            removeMessages(MSG_UPDATE_SUGGESTION_STRIP)
        }

        fun hasPendingUpdateSuggestions(): Boolean {
            return hasMessages(MSG_UPDATE_SUGGESTION_STRIP)
        }

        fun hasPendingReopenDictionaries(): Boolean {
            return hasMessages(MSG_REOPEN_DICTIONARIES)
        }

        fun postUpdateShiftState() {
            removeMessages(MSG_UPDATE_SHIFT_STATE)
            sendMessageDelayed(obtainMessage(MSG_UPDATE_SHIFT_STATE),
                    mDelayInMillisecondsToUpdateShiftState.toLong())
        }

        fun postDeallocateMemory() {
            sendMessageDelayed(obtainMessage(MSG_DEALLOCATE_MEMORY),
                    DELAY_DEALLOCATE_MEMORY_MILLIS)
        }

        fun cancelDeallocateMemory() {
            removeMessages(MSG_DEALLOCATE_MEMORY)
        }

        fun hasPendingDeallocateMemory(): Boolean {
            return hasMessages(MSG_DEALLOCATE_MEMORY)
        }

        fun showGesturePreviewAndSuggestionStrip(suggestedWords: SuggestedWords,
                                                 dismissGestureFloatingPreviewText: Boolean) {
            removeMessages(MSG_SHOW_GESTURE_PREVIEW_AND_SUGGESTION_STRIP)
            val arg1 = if (dismissGestureFloatingPreviewText)
                ARG1_DISMISS_GESTURE_FLOATING_PREVIEW_TEXT
            else
                ARG1_SHOW_GESTURE_FLOATING_PREVIEW_TEXT
            obtainMessage(MSG_SHOW_GESTURE_PREVIEW_AND_SUGGESTION_STRIP, arg1,
                    ARG2_UNUSED, suggestedWords).sendToTarget()
        }

        fun showSuggestionStrip(suggestedWords: SuggestedWords) {
            removeMessages(MSG_SHOW_GESTURE_PREVIEW_AND_SUGGESTION_STRIP)
            obtainMessage(MSG_SHOW_GESTURE_PREVIEW_AND_SUGGESTION_STRIP,
                    ARG1_NOT_GESTURE_INPUT, ARG2_UNUSED, suggestedWords).sendToTarget()
        }

        fun showTailBatchInputResult(suggestedWords: SuggestedWords) {
            obtainMessage(MSG_UPDATE_TAIL_BATCH_INPUT_COMPLETED, suggestedWords).sendToTarget()
        }

        fun postSwitchLanguage(subtype: InputMethodSubtype) {
            obtainMessage(MSG_SWITCH_LANGUAGE_AUTOMATICALLY, subtype).sendToTarget()
        }

        fun startOrientationChanging() {
            removeMessages(MSG_PENDING_IMS_CALLBACK)
            resetPendingImsCallback()
            mIsOrientationChanging = true
            val latinIme = ownerInstance ?: return
            if (latinIme.isInputViewShown) {
                latinIme.mKeyboardSwitcher.saveKeyboardState()
            }
        }

        private fun resetPendingImsCallback() {
            mHasPendingFinishInputView = false
            mHasPendingFinishInput = false
            mHasPendingStartInput = false
        }

        private fun executePendingImsCallback(latinIme: LatinIME, editorInfo: EditorInfo?,
                                              restarting: Boolean) {
            if (mHasPendingFinishInputView) {
                latinIme.onFinishInputViewInternal(mHasPendingFinishInput)
            }
            if (mHasPendingFinishInput) {
                latinIme.onFinishInputInternal()
            }
            if (mHasPendingStartInput) {
                latinIme.onStartInputInternal(editorInfo, restarting)
            }
            resetPendingImsCallback()
        }

        fun onStartInput(editorInfo: EditorInfo, restarting: Boolean) {
            if (hasMessages(MSG_PENDING_IMS_CALLBACK)) {
                // Typically this is the second onStartInput after orientation changed.
                mHasPendingStartInput = true
            } else {
                if (mIsOrientationChanging && restarting) {
                    // This is the first onStartInput after orientation changed.
                    mIsOrientationChanging = false
                    mPendingSuccessiveImsCallback = true
                }
                val latinIme = ownerInstance
                if (latinIme != null) {
                    executePendingImsCallback(latinIme, editorInfo, restarting)
                    latinIme.onStartInputInternal(editorInfo, restarting)
                }
            }
        }

        fun onStartInputView(editorInfo: EditorInfo, restarting: Boolean) {
            if (hasMessages(MSG_PENDING_IMS_CALLBACK) && KeyboardId.equivalentEditorInfoForKeyboard(editorInfo, mAppliedEditorInfo)) {
                // Typically this is the second onStartInputView after orientation changed.
                resetPendingImsCallback()
            } else {
                if (mPendingSuccessiveImsCallback) {
                    // This is the first onStartInputView after orientation changed.
                    mPendingSuccessiveImsCallback = false
                    resetPendingImsCallback()
                    sendMessageDelayed(obtainMessage(MSG_PENDING_IMS_CALLBACK),
                            PENDING_IMS_CALLBACK_DURATION_MILLIS.toLong())
                }
                val latinIme = ownerInstance
                if (latinIme != null) {
                    executePendingImsCallback(latinIme, editorInfo, restarting)
                    latinIme.onStartInputViewInternal(editorInfo, restarting)
                    mAppliedEditorInfo = editorInfo
                }
                cancelDeallocateMemory()
            }
        }

        fun onFinishInputView(finishingInput: Boolean) {
            if (hasMessages(MSG_PENDING_IMS_CALLBACK)) {
                // Typically this is the first onFinishInputView after orientation changed.
                mHasPendingFinishInputView = true
            } else {
                val latinIme = ownerInstance
                if (latinIme != null) {
                    latinIme.onFinishInputViewInternal(finishingInput)
                    mAppliedEditorInfo = null
                }
                if (!hasPendingDeallocateMemory()) {
                    postDeallocateMemory()
                }
            }
        }

        fun onFinishInput() {
            if (hasMessages(MSG_PENDING_IMS_CALLBACK)) {
                // Typically this is the first onFinishInput after orientation changed.
                mHasPendingFinishInput = true
            } else {
                val latinIme = ownerInstance
                if (latinIme != null) {
                    executePendingImsCallback(latinIme, null, false)
                    latinIme.onFinishInputInternal()
                }
            }
        }

        companion object {
            private val MSG_UPDATE_SHIFT_STATE = 0
            private val MSG_PENDING_IMS_CALLBACK = 1
            private val MSG_UPDATE_SUGGESTION_STRIP = 2
            private val MSG_SHOW_GESTURE_PREVIEW_AND_SUGGESTION_STRIP = 3
            private val MSG_RESUME_SUGGESTIONS = 4
            private val MSG_REOPEN_DICTIONARIES = 5
            private val MSG_UPDATE_TAIL_BATCH_INPUT_COMPLETED = 6
            private val MSG_RESET_CACHES = 7
            private val MSG_WAIT_FOR_DICTIONARY_LOAD = 8
            private val MSG_DEALLOCATE_MEMORY = 9
            private val MSG_RESUME_SUGGESTIONS_FOR_START_INPUT = 10
            private val MSG_SWITCH_LANGUAGE_AUTOMATICALLY = 11
            // Update this when adding new messages
            private val MSG_LAST = MSG_SWITCH_LANGUAGE_AUTOMATICALLY

            private val ARG1_NOT_GESTURE_INPUT = 0
            private val ARG1_DISMISS_GESTURE_FLOATING_PREVIEW_TEXT = 1
            private val ARG1_SHOW_GESTURE_FLOATING_PREVIEW_TEXT = 2
            private val ARG2_UNUSED = 0
            private val ARG1_TRUE = 1
        }
    }

    internal class SubtypeState {
        private var mLastActiveSubtype: InputMethodSubtype? = null
        private var mCurrentSubtypeHasBeenUsed: Boolean = false

        fun setCurrentSubtypeHasBeenUsed() {
            mCurrentSubtypeHasBeenUsed = true
        }

        fun switchSubtype(token: IBinder, richImm: RichInputMethodManager) {
            val currentSubtype = richImm.inputMethodManager
                    .currentInputMethodSubtype
            val lastActiveSubtype = mLastActiveSubtype
            val currentSubtypeHasBeenUsed = mCurrentSubtypeHasBeenUsed
            if (currentSubtypeHasBeenUsed) {
                mLastActiveSubtype = currentSubtype
                mCurrentSubtypeHasBeenUsed = false
            }
            if (currentSubtypeHasBeenUsed
                    && richImm.checkIfSubtypeBelongsToThisImeAndEnabled(lastActiveSubtype)
                    && currentSubtype != lastActiveSubtype) {
                richImm.setInputMethodAndSubtype(token, lastActiveSubtype)
                return
            }
            richImm.switchToNextInputMethod(token, true /* onlyCurrentIme */)
        }
    }

    init {
        mSettings = Settings.getInstance()
        mKeyboardSwitcher = KeyboardSwitcher.instance
        mStatsUtilsManager = StatsUtilsManager.getInstance()
        @Suppress("DEPRECATION")
        mIsHardwareAcceleratedDrawingEnabled = enableHardwareAcceleration()
        Log.i(TAG, "Hardware accelerated drawing: $mIsHardwareAcceleratedDrawingEnabled")
    }

    override fun onCreate() {
        ExceptionLogger.init(this)
        Settings.init(this)
        DebugFlags.init(PreferenceManager.getDefaultSharedPreferences(this))
        RichInputMethodManager.init(this)
        mRichImm = RichInputMethodManager.getInstance()
        KeyboardSwitcher.init(this)
        AudioAndHapticFeedbackManager.init(this)
        AccessibilityUtils.init(this)
        mStatsUtilsManager.onCreate(this /* context */, mDictionaryFacilitator)
        TypefaceUtils.init(this)
        super.onCreate()

        mHandler.onCreate()

        val locale = mRichImm?.currentSubtypeLocale

        // Init deadkey combiners and InputLogic
        // The dead key combiner is always active, and always first
        val combiners: MutableList<Combiner> = mutableListOf(DeadKeyCombiner())

        Log.d("onCreate", "Locale: $locale")
        if (locale != null) {
            val keyboardDescriptor = loadKeyboardDescriptor(this, locale)
            keyboardDescriptor?.let {
                combiners += SoftDeadKeyCombiner(keyboardDescriptor.transforms)
            }
        }

        mInputLogic = InputLogic(this, this, mDictionaryFacilitator, combiners)

        // TODO: Resolve mutual dependencies of {@link #loadSettings()} and
        // {@link #resetDictionaryFacilitatorIfNecessary()}.
        loadSettings()
        resetDictionaryFacilitatorIfNecessary()

        // Register to receive ringer mode change.
        val filter = IntentFilter()
        filter.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            registerReceiver(mRingerModeChangeReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(mRingerModeChangeReceiver, filter)
        }

        val dictDumpFilter = IntentFilter()
        dictDumpFilter.addAction(DictionaryDumpBroadcastReceiver.DICTIONARY_DUMP_INTENT_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            registerReceiver(mDictionaryDumpBroadcastReceiver, dictDumpFilter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(mDictionaryDumpBroadcastReceiver, dictDumpFilter)
        }

        StatsUtils.onCreate(mSettings.current, mRichImm)
        
        // Handle edge-to-edge for Android 15+ InputMethodService
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            handleEdgeToEdgeForIme()
        }
    }

    // Has to be package-visible for unit tests
    @UsedForTesting
    internal fun loadSettings() {

        val locale = mRichImm?.currentSubtypeLocale

        val editorInfo = currentInputEditorInfo
        val inputAttributes = InputAttributes(
                editorInfo, isFullscreenMode, packageName)
        mSettings.loadSettings(this, locale, inputAttributes)
        val currentSettingsValues = mSettings.current
        AudioAndHapticFeedbackManager.instance.onSettingsChanged(currentSettingsValues)
        // This method is called on startup and language switch, before the new layout has
        // been displayed. Opening dictionaries never affects responsivity as dictionaries are
        // asynchronously loaded.
        if (!mHandler.hasPendingReopenDictionaries()) {
            resetDictionaryFacilitator(locale)
        }
        resetDictionaryFacilitatorIfNecessary()
        mStatsUtilsManager.onLoadSettings(this /* context */, currentSettingsValues)
    }

    // Note that this method is called from a non-UI thread.
    override fun onUpdateMainDictionaryAvailability(isMainDictionaryAvailable: Boolean) {
        val mainKeyboardView = mKeyboardSwitcher.mainKeyboardView
        mainKeyboardView?.setMainDictionaryAvailability(isMainDictionaryAvailable)
        if (mHandler.hasPendingWaitForDictionaryLoad()) {
            mHandler.cancelWaitForDictionaryLoad()
            mHandler.postResumeSuggestions(false /* shouldDelay */)
        }
    }

    internal fun resetDictionaryFacilitatorIfNecessary() {
        val subtypeSwitcherLocale = mRichImm?.currentSubtypeLocale
        val subtypeLocale: Locale
        if (subtypeSwitcherLocale == null) {
            // This happens in very rare corner cases - for example, immediately after a switch
            // to LatinIME has been requested, about a frame later another switch happens. In this
            // case, we are about to go down but we still don't know it, however the system tells
            // us there is no current subtype.
            Log.e(TAG, "System is reporting no current subtype.")
            subtypeLocale = resources.configuration.locale
        } else {
            subtypeLocale = subtypeSwitcherLocale
        }
        if (mDictionaryFacilitator != null) {
            if (mDictionaryFacilitator.isForLocale(subtypeLocale) && mDictionaryFacilitator.isForAccount(mSettings.current.mAccount)) {
                return
            }
        }
        resetDictionaryFacilitator(subtypeLocale)
    }

    /**
     * Reset the facilitator by loading dictionaries for the given locale and
     * the current settings values.
     *
     * @param locale the locale
     */
    // TODO: make sure the current settings always have the right locales, and read from them.
    private fun resetDictionaryFacilitator(locale: Locale?) {
        val settingsValues = mSettings.current
        mDictionaryFacilitator?.resetDictionaries(this /* context */, locale,
                settingsValues.mUseContactsDict, false, // settingsValues.mUsePersonalizedDicts,
                false /* forceReloadMainDictionary */,
                settingsValues.mAccount, "" /* dictNamePrefix */,
                this /* DictionaryInitializationListener */)
        if (settingsValues.mAutoCorrectionEnabledPerUserSettings) {
            mInputLogic.suggest.setAutoCorrectionThreshold(
                    settingsValues.mAutoCorrectionThreshold)
        }
        mInputLogic.suggest.setPlausibilityThreshold(settingsValues.mPlausibilityThreshold)
    }

    /**
     * Reset suggest by loading the main dictionary of the current locale.
     */
    /* package private */ internal fun resetSuggestMainDict() {
        val settingsValues = mSettings.current
        mDictionaryFacilitator?.resetDictionaries(this /* context */,
                mDictionaryFacilitator.locale, settingsValues.mUseContactsDict,

                false, // settingsValues.mUsePersonalizedDicts,
                true /* forceReloadMainDictionary */,
                settingsValues.mAccount, "" /* dictNamePrefix */,
                this /* DictionaryInitializationListener */)
    }

    override fun onDestroy() {
        Timber.d("onDestroy")
        mDictionaryFacilitator.closeDictionaries()
        mSettings.onDestroy()
        unregisterReceiver(mRingerModeChangeReceiver)
        unregisterReceiver(mDictionaryDumpBroadcastReceiver)
        mStatsUtilsManager.onDestroy(this /* context */)
        mSpacerView = null
        super.onDestroy()
    }

    @UsedForTesting
    fun recycle() {
        unregisterReceiver(mDictionaryDumpBroadcastReceiver)
        unregisterReceiver(mRingerModeChangeReceiver)
        mInputLogic.recycle()
    }

    override fun onConfigurationChanged(conf: Configuration) {
        var settingsValues = mSettings.current
        if (settingsValues.mDisplayOrientation != conf.orientation) {
            mHandler.startOrientationChanging()
            mInputLogic.onOrientationChange(mSettings.current)
        }
        if (settingsValues.mHasHardwareKeyboard != Settings.readHasHardwareKeyboard(conf)) {
            // If the state of having a hardware keyboard changed, then we want to reload the
            // settings to adjust for that.
            // TODO: we should probably do this unconditionally here, rather than only when we
            // have a change in hardware keyboard configuration.
            loadSettings()
            settingsValues = mSettings.current
            if (isImeSuppressedByHardwareKeyboard) {
                // We call cleanupInternalStateForFinishInput() because it's the right thing to do;
                // however, it seems at the moment the framework is passing us a seemingly valid
                // but actually non-functional InputConnection object. So if this bug ever gets
                // fixed we'll be able to remove the composition, but until it is this code is
                // actually not doing much.
                cleanupInternalStateForFinishInput()
            }
        }
        super.onConfigurationChanged(conf)
    }

    override fun onCreateInputView(): View {
        StatsUtils.onCreateInputView()
        return mKeyboardSwitcher.onCreateInputView(mIsHardwareAcceleratedDrawingEnabled)
    }

    override fun setInputView(view: View) {
        super.setInputView(view)
        mInputView = view
        mInsetsUpdater = ViewOutlineProviderCompatUtils.setInsetsOutlineProvider(view)
        updateSoftInputWindowLayoutParameters()
        
        // Reset spacer view when input view changes
        mSpacerView = null
        
        // Setup insets handling for edge-to-edge on Android 15+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            setupInputViewInsets(view)
        }
        
        mSuggestionStripView = view.findViewById<View>(R.id.suggestion_strip_view) as SuggestionStripView
        if (hasSuggestionStripView()) {
            mSuggestionStripView!!.setListener(this, view)
        }
    }

    private fun handleEdgeToEdgeForIme() {
        try {
            val window = window?.window
            if (window != null) {
                // Enable edge-to-edge for the IME window
                WindowCompat.setDecorFitsSystemWindows(window, false)
                
                // Set light navigation bar for proper button contrast
                // This tells the system to use dark buttons/icons on our light keyboard background
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // Use modern WindowInsetsController for Android 11+ (API 30)
                    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                    insetsController.isAppearanceLightNavigationBars = true
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // Fallback to deprecated method for Android 8-10
                    @Suppress("DEPRECATION")
                    window.decorView.systemUiVisibility = (
                        window.decorView.systemUiVisibility or
                        android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to setup edge-to-edge for InputMethodService", e)
        }
    }
    
    private fun setupInputViewInsets(view: View) {
        // Apply system bar insets to the keyboard view to prevent overlapping with navigation buttons
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            
            // Add a bottom spacer to make the keyboard view taller instead of using padding
            // Padding shows transparency that looks broken
            if (systemBars.bottom > 0) {
                addBottomSpacerToKeyboard(v, systemBars.bottom)
            }
            
            insets
        }
    }
    
    private fun addBottomSpacerToKeyboard(inputView: View, spacerHeight: Int) {
        val mainKeyboardFrame = inputView.findViewById<android.view.ViewGroup>(R.id.main_keyboard_frame)
        mainKeyboardFrame?.let { frameLayout ->
            if (mSpacerView == null) {
                val keyboardView = frameLayout.findViewById<View>(R.id.keyboard_view)
                mSpacerView = View(this).apply {
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        spacerHeight
                    )
                    keyboardView?.background?.let { keyboardBackground ->
                        background = keyboardBackground.constantState?.newDrawable()
                    }
                }
                
                frameLayout.addView(mSpacerView)
            } else {
                // Update existing spacer height
                val layoutParams = mSpacerView?.layoutParams
                layoutParams?.height = spacerHeight
                mSpacerView?.layoutParams = layoutParams
            }
        }
    }

    override fun setCandidatesView(view: View) {
        // To ensure that CandidatesView will never be set.
    }

    override fun onStartInput(editorInfo: EditorInfo, restarting: Boolean) {
        restartUpdaterIfSubtypesChanged()
        mHandler.onStartInput(editorInfo, restarting)
    }

    override fun onStartInputView(editorInfo: EditorInfo, restarting: Boolean) {
        mHandler.onStartInputView(editorInfo, restarting)
        mStatsUtilsManager.onStartInputView()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        StatsUtils.onFinishInputView()
        mHandler.onFinishInputView(finishingInput)
        mStatsUtilsManager.onFinishInputView()
        mGestureConsumer = GestureConsumer.NULL_GESTURE_CONSUMER
    }

    override fun onFinishInput() {
        mHandler.onFinishInput()
    }

    public override fun onCurrentInputMethodSubtypeChanged(subtype: InputMethodSubtype) {
        // Note that the calling sequence of onCreate() and onCurrentInputMethodSubtypeChanged()
        // is not guaranteed. It may even be called at the same time on a different thread.
        val oldSubtype = mRichImm?.currentSubtype?.rawSubtype
        StatsUtils.onSubtypeChanged(oldSubtype, subtype)
        mRichImm?.onSubtypeChanged(subtype)
        mInputLogic.onSubtypeChanged(SubtypeLocaleUtils.getCombiningRulesExtraValue(subtype),
                mSettings.current)
        loadKeyboard()
    }

    internal fun onStartInputInternal(editorInfo: EditorInfo?, restarting: Boolean) {
        super.onStartInput(editorInfo, restarting)

        // If the primary hint language does not match the current subtype language, then try
        // to switch to the primary hint language.
        // TODO: Support all the locales in EditorInfo#hintLocales.
        val primaryHintLocale = EditorInfoCompatUtils.getPrimaryHintLocale(editorInfo) ?: return
        val newSubtype = mRichImm?.findSubtypeByLocale(primaryHintLocale)
        if (newSubtype == null || newSubtype == mRichImm?.currentSubtype?.rawSubtype) {
            return
        }
        mHandler.postSwitchLanguage(newSubtype)
    }

    internal fun onStartInputViewInternal(editorInfo: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(editorInfo, restarting)

        mDictionaryFacilitator?.onStartInput()
        // Switch to the null consumer to handle cases leading to early exit below, for which we
        // also wouldn't be consuming gesture data.
        mGestureConsumer = GestureConsumer.NULL_GESTURE_CONSUMER
        mRichImm?.refreshSubtypeCaches()
        val switcher = mKeyboardSwitcher
        switcher.updateKeyboardTheme()
        val mainKeyboardView = switcher.mainKeyboardView
        // If we are starting input in a different text field from before, we'll have to reload
        // settings, so currentSettingsValues can't be final.
        var currentSettingsValues = mSettings.current

        if (editorInfo == null) {
            Log.e(TAG, "Null EditorInfo in onStartInputView()")
            if (DebugFlags.DEBUG_ENABLED) {
                throw NullPointerException("Null EditorInfo in onStartInputView()")
            }
            return
        }
        if (DebugFlags.DEBUG_ENABLED) {
            Log.d(TAG, "onStartInputView: editorInfo:" + String.format("inputType=0x%08x imeOptions=0x%08x",
                    editorInfo.inputType, editorInfo.imeOptions))
            Log.d(TAG, "All caps = "
                    + (editorInfo.inputType and InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS != 0)
                    + ", sentence caps = "
                    + (editorInfo.inputType and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES != 0)
                    + ", word caps = "
                    + (editorInfo.inputType and InputType.TYPE_TEXT_FLAG_CAP_WORDS != 0))
        }
        Log.i(TAG, "Starting input. Cursor position = "
                + editorInfo.initialSelStart + "," + editorInfo.initialSelEnd)
        // TODO: Consolidate these checks with {@link InputAttributes}.
        if (InputAttributes.inPrivateImeOptions(null, NO_MICROPHONE_COMPAT, editorInfo)) {
            Log.w(TAG, "Deprecated private IME option specified: " + editorInfo.privateImeOptions)
            Log.w(TAG, "Use $packageName.$NO_MICROPHONE instead")
        }
        if (InputAttributes.inPrivateImeOptions(packageName, FORCE_ASCII, editorInfo)) {
            Log.w(TAG, "Deprecated private IME option specified: " + editorInfo.privateImeOptions)
            Log.w(TAG, "Use EditorInfo.IME_FLAG_FORCE_ASCII flag instead")
        }

        // In landscape mode, this method gets called without the input view being created.
        if (mainKeyboardView == null) {
            return
        }

        // Update to a gesture consumer with the current editor and IME state.
        mGestureConsumer = GestureConsumer.newInstance(editorInfo,
                mInputLogic.privateCommandPerformer,
                mRichImm?.currentSubtypeLocale,
                switcher.keyboard)

        // Forward this event to the accessibility utilities, if enabled.
        val accessUtils = AccessibilityUtils.getInstance()
        if (accessUtils.isTouchExplorationEnabled) {
            accessUtils.onStartInputViewInternal(mainKeyboardView, editorInfo, restarting)
        }

        val inputTypeChanged = !currentSettingsValues.isSameInputType(editorInfo)
        val isDifferentTextField = !restarting || inputTypeChanged

        StatsUtils.onStartInputView(editorInfo.inputType,
                Settings.getInstance().current.mDisplayOrientation,
                !isDifferentTextField)

        // The EditorInfo might have a flag that affects fullscreen mode.
        // Note: This call should be done by InputMethodService?
        updateFullscreenMode()

        // ALERT: settings have not been reloaded and there is a chance they may be stale.
        // In the practice, if it is, we should have gotten onConfigurationChanged so it should
        // be fine, but this is horribly confusing and must be fixed AS SOON AS POSSIBLE.

        // In some cases the input connection has not been reset yet and we can't access it. In
        // this case we will need to call loadKeyboard() later, when it's accessible, so that we
        // can go into the correct mode, so we need to do some housekeeping here.
        val needToCallLoadKeyboardLater: Boolean
        val suggest = mInputLogic.suggest
        if (!isImeSuppressedByHardwareKeyboard) {
            // The app calling setText() has the effect of clearing the composing
            // span, so we should reset our state unconditionally, even if restarting is true.
            // We also tell the input logic about the combining rules for the current subtype, so
            // it can adjust its combiners if needed.

            mInputLogic.startInput(mRichImm?.combiningRulesExtraValueOfCurrentSubtype,
                    currentSettingsValues)

            resetDictionaryFacilitatorIfNecessary()

            // TODO[IL]: Can the following be moved to InputLogic#startInput?
            if (!/* shouldFinishComposition */mInputLogic.mConnection.resetCachesUponCursorMoveAndReturnSuccess(
                            editorInfo.initialSelStart, editorInfo.initialSelEnd,
                            false)) {
                // Sometimes, while rotating, for some reason the framework tells the app we are not
                // connected to it and that means we can't refresh the cache. In this case, schedule
                // a refresh later.
                // We try resetting the caches up to 5 times before giving up.
                mHandler.postResetCaches(isDifferentTextField, 5 /* remainingTries */)
                // mLastSelection{Start,End} are reset later in this method, no need to do it here
                needToCallLoadKeyboardLater = true
            } else {
                // When rotating, and when input is starting again in a field from where the focus
                // didn't move (the keyboard having been closed with the back key),
                // initialSelStart and initialSelEnd sometimes are lying. Make a best effort to
                // work around this bug.
                mInputLogic.mConnection.tryFixLyingCursorPosition()
                mHandler.postResumeSuggestionsForStartInput(true /* shouldDelay */)
                needToCallLoadKeyboardLater = false
            }
        } else {
            // If we have a hardware keyboard we don't need to call loadKeyboard later anyway.
            needToCallLoadKeyboardLater = false
        }

        if (isDifferentTextField || !currentSettingsValues.hasSameOrientation(resources.configuration)) {
            loadSettings()
        }
        if (isDifferentTextField) {
            mainKeyboardView.closing()
            currentSettingsValues = mSettings.current

            if (currentSettingsValues.mAutoCorrectionEnabledPerUserSettings) {
                suggest.setAutoCorrectionThreshold(
                        currentSettingsValues.mAutoCorrectionThreshold)
            }
            suggest.setPlausibilityThreshold(currentSettingsValues.mPlausibilityThreshold)

            switcher.loadKeyboard(editorInfo, currentSettingsValues, currentAutoCapsState,
                    currentRecapitalizeState)
            if (needToCallLoadKeyboardLater) {
                // If we need to call loadKeyboard again later, we need to save its state now. The
                // later call will be done in #retryResetCaches.
                switcher.saveKeyboardState()
            }
        } else if (restarting) {
            // TODO: Come up with a more comprehensive way to reset the keyboard layout when
            // a keyboard layout set doesn't get reloaded in this method.
            switcher.resetKeyboardStateToAlphabet(currentAutoCapsState,
                    currentRecapitalizeState)
            // In apps like Talk, we come here when the text is sent and the field gets emptied and
            // we need to re-evaluate the shift state, but not the whole layout which would be
            // disruptive.
            // Space state must be updated before calling updateShiftState
            switcher.requestUpdatingShiftState(currentAutoCapsState,
                    currentRecapitalizeState)
        }
        // This will set the punctuation suggestions if next word suggestion is off;
        // otherwise it will clear the suggestion strip.
        setNeutralSuggestionStrip()

        mHandler.cancelUpdateSuggestionStrip()

        mainKeyboardView.setMainDictionaryAvailability(
                mDictionaryFacilitator?.hasAtLeastOneInitializedMainDictionary() ?: false)
        mainKeyboardView.setKeyPreviewPopupEnabled(currentSettingsValues.mKeyPreviewPopupOn,
                currentSettingsValues.mKeyPreviewPopupDismissDelay)
        mainKeyboardView.setSlidingKeyInputPreviewEnabled(
                currentSettingsValues.mSlidingKeyInputPreviewEnabled)
        mainKeyboardView.setGestureHandlingEnabledByUser(
                currentSettingsValues.mGestureInputEnabled,
                currentSettingsValues.mGestureTrailEnabled,
                currentSettingsValues.mGestureFloatingPreviewTextEnabled)
    }

    override fun onWindowShown() {
        super.onWindowShown()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setNavigationBarVisibility(isInputViewShown)
        }
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        val mainKeyboardView = mKeyboardSwitcher.mainKeyboardView
        mainKeyboardView?.closing()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setNavigationBarVisibility(false)
        }
    }

    internal fun onFinishInputInternal() {
        super.onFinishInput()

        mDictionaryFacilitator?.onFinishInput(this)
        val mainKeyboardView = mKeyboardSwitcher.mainKeyboardView
        mainKeyboardView?.closing()
    }

    internal fun onFinishInputViewInternal(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        cleanupInternalStateForFinishInput()
    }

    private fun cleanupInternalStateForFinishInput() {
        // Remove pending messages related to update suggestions
        mHandler.cancelUpdateSuggestionStrip()
        // Should do the following in onFinishInputInternal but until JB MR2 it's not called :(
        mInputLogic.finishInput()
    }

    protected fun deallocateMemory() {
        mKeyboardSwitcher.deallocateMemory()
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int,
                                   newSelStart: Int, newSelEnd: Int,
                                   composingSpanStart: Int, composingSpanEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd,
                composingSpanStart, composingSpanEnd)
        if (DebugFlags.DEBUG_ENABLED) {
            Log.i(TAG, "onUpdateSelection: oss=" + oldSelStart + ", ose=" + oldSelEnd
                    + ", nss=" + newSelStart + ", nse=" + newSelEnd
                    + ", cs=" + composingSpanStart + ", ce=" + composingSpanEnd)
        }

        // This call happens whether our view is displayed or not, but if it's not then we should
        // not attempt recorrection. This is true even with a hardware keyboard connected: if the
        // view is not displayed we have no means of showing suggestions anyway, and if it is then
        // we want to show suggestions anyway.
        val settingsValues = mSettings.current
        if (isInputViewShown && mInputLogic.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd,
                        settingsValues)) {
            mKeyboardSwitcher.requestUpdatingShiftState(currentAutoCapsState,
                    currentRecapitalizeState)
        }
    }

    /**
     * This is called when the user has clicked on the extracted text view,
     * when running in fullscreen mode.  The default implementation hides
     * the suggestions view when this happens, but only if the extracted text
     * editor has a vertical scroll bar because its text doesn't fit.
     * Here we override the behavior due to the possibility that a re-correction could
     * cause the suggestions strip to disappear and re-appear.
     */
    override fun onExtractedTextClicked() {
        if (mSettings.current.needsToLookupSuggestions()) {
            return
        }

        super.onExtractedTextClicked()
    }

    /**
     * This is called when the user has performed a cursor movement in the
     * extracted text view, when it is running in fullscreen mode.  The default
     * implementation hides the suggestions view when a vertical movement
     * happens, but only if the extracted text editor has a vertical scroll bar
     * because its text doesn't fit.
     * Here we override the behavior due to the possibility that a re-correction could
     * cause the suggestions strip to disappear and re-appear.
     */
    override fun onExtractedCursorMovement(dx: Int, dy: Int) {
        if (mSettings.current.needsToLookupSuggestions()) {
            return
        }

        super.onExtractedCursorMovement(dx, dy)
    }

    override fun hideWindow() {
        mKeyboardSwitcher.onHideWindow()

        if (TRACE) Debug.stopMethodTracing()
        if (isShowingOptionDialog) {
            mOptionsDialog!!.dismiss()
            mOptionsDialog = null
        }
        super.hideWindow()
    }

    override fun onDisplayCompletions(applicationSpecifiedCompletions: Array<CompletionInfo>?) {
        Log.d("LatinIME", "onDisplayCompletions")
        if (DebugFlags.DEBUG_ENABLED) {
            Log.i(TAG, "Received completions:")
            if (applicationSpecifiedCompletions != null) {
                for (i in applicationSpecifiedCompletions.indices) {
                    Log.i(TAG, "  #" + i + ": " + applicationSpecifiedCompletions[i])
                }
            }
        }
        if (!mSettings.current.isApplicationSpecifiedCompletionsOn) {
            return
        }
        // If we have an update request in flight, we need to cancel it so it does not override
        // these completions.
        mHandler.cancelUpdateSuggestionStrip()
        if (applicationSpecifiedCompletions == null) {
            setNeutralSuggestionStrip()
            return
        }

        val applicationSuggestedWords = SuggestedWords.getFromApplicationSpecifiedCompletions(
                applicationSpecifiedCompletions)
        val suggestedWords = SuggestedWords(applicationSuggestedWords, null, null,
                false /* typedWordValid */,
                false /* willAutoCorrect */,
                false /* isObsoleteSuggestions */,
                SuggestedWords.INPUT_STYLE_APPLICATION_SPECIFIED /* inputStyle */,
                SuggestedWords.NOT_A_SEQUENCE_NUMBER)/* rawSuggestions *//* typedWord */
        // When in fullscreen mode, show completions generated by the application forcibly
        setSuggestedWords(suggestedWords)
    }

    override fun onComputeInsets(outInsets: InputMethodService.Insets) {
        super.onComputeInsets(outInsets)
        // This method may be called before {@link #setInputView(View)}.
        if (mInputView == null) {
            return
        }
        val settingsValues = mSettings.current
        val visibleKeyboardView = mKeyboardSwitcher.visibleKeyboardView
        if (visibleKeyboardView == null || !hasSuggestionStripView()) {
            return
        }
        val inputHeight = mInputView!!.height
        if (isImeSuppressedByHardwareKeyboard && !visibleKeyboardView.isShown) {
            // If there is a hardware keyboard and a visible software keyboard view has been hidden,
            // no visual element will be shown on the screen.
            outInsets.contentTopInsets = inputHeight
            outInsets.visibleTopInsets = inputHeight
            mInsetsUpdater!!.setInsets(outInsets)
            return
        }
        val suggestionsHeight = if (!mKeyboardSwitcher.isShowingEmojiPalettes && mSuggestionStripView!!.visibility == View.VISIBLE)
            mSuggestionStripView!!.height
        else
            0
        val spacerHeight = mSpacerView?.layoutParams?.height ?: 0
        val visibleTopY = inputHeight - visibleKeyboardView.height - suggestionsHeight - spacerHeight
        mSuggestionStripView!!.setMoreSuggestionsHeight(visibleTopY)
        // Need to set expanded touchable region only if a keyboard view is being shown.
        if (visibleKeyboardView.isShown) {
            val touchLeft = 0
            val touchTop = if (mKeyboardSwitcher.isShowingMoreKeysPanel) 0 else visibleTopY
            val touchRight = visibleKeyboardView.width
            val touchBottom = (inputHeight
                    // Extend touchable region below the keyboard.
                    + EXTENDED_TOUCHABLE_REGION_HEIGHT)
            outInsets.touchableInsets = InputMethodService.Insets.TOUCHABLE_INSETS_REGION
            outInsets.touchableRegion.set(touchLeft, touchTop, touchRight, touchBottom)
        }
        outInsets.contentTopInsets = visibleTopY
        outInsets.visibleTopInsets = visibleTopY
        mInsetsUpdater!!.setInsets(outInsets)
    }

    fun startShowingInputView(needsToLoadKeyboard: Boolean) {
        mIsExecutingStartShowingInputView = true
        // This {@link #showWindow(boolean)} will eventually call back
        // {@link #onEvaluateInputViewShown()}.
        showWindow(true /* showInput */)
        mIsExecutingStartShowingInputView = false
        if (needsToLoadKeyboard) {
            loadKeyboard()
        }
    }

    fun stopShowingInputView() {
        showWindow(false /* showInput */)
    }

    override fun onShowInputRequested(flags: Int, configChange: Boolean): Boolean {
        // Bypass hardware keyboard detection in debug builds
        if (BuildConfig.DEBUG) {
            return super.onShowInputRequested(flags, configChange)
        }
        
        return if (isImeSuppressedByHardwareKeyboard) {
            true
        } else super.onShowInputRequested(flags, configChange)
    }

    override fun onEvaluateInputViewShown(): Boolean {
        // Always show keyboard in debug builds
        if (BuildConfig.DEBUG) {
            return true
        }
        
        return if (mIsExecutingStartShowingInputView) {
            true
        } else super.onEvaluateInputViewShown()
    }

    override fun onEvaluateFullscreenMode(): Boolean {
        val settingsValues = mSettings.current
        if (isImeSuppressedByHardwareKeyboard) {
            // If there is a hardware keyboard, disable full screen mode.
            return false
        }
        // Reread resource value here, because this method is called by the framework as needed.
        val isFullscreenModeAllowed = Settings.readUseFullscreenMode(resources)
        if (super.onEvaluateFullscreenMode() && isFullscreenModeAllowed) {
            // TODO: Remove this hack. Actually we should not really assume NO_EXTRACT_UI
            // implies NO_FULLSCREEN. However, the framework mistakenly does.  i.e. NO_EXTRACT_UI
            // without NO_FULLSCREEN doesn't work as expected. Because of this we need this
            // hack for now.  Let's get rid of this once the framework gets fixed.
            val ei = currentInputEditorInfo
            return !(ei != null && ei.imeOptions and EditorInfo.IME_FLAG_NO_EXTRACT_UI != 0)
        }
        return false
    }

    override fun updateFullscreenMode() {
        super.updateFullscreenMode()
        updateSoftInputWindowLayoutParameters()
    }

    private fun updateSoftInputWindowLayoutParameters() {
        // Override layout parameters to expand {@link SoftInputWindow} to the entire screen.
        // See {@link InputMethodService#setinputView(View)} and
        // {@link SoftInputWindow#updateWidthHeight(WindowManager.LayoutParams)}.
        val window = window.window
        ViewLayoutUtils.updateLayoutHeightOf(window!!, LayoutParams.MATCH_PARENT)
        // This method may be called before {@link #setInputView(View)}.
        if (mInputView != null) {
            // In non-fullscreen mode, {@link InputView} and its parent inputArea should expand to
            // the entire screen and be placed at the bottom of {@link SoftInputWindow}.
            // In fullscreen mode, these shouldn't expand to the entire screen and should be
            // coexistent with {@link #mExtractedArea} above.
            // See {@link InputMethodService#setInputView(View) and
            // com.android.internal.R.layout.input_method.xml.
            val layoutHeight = if (isFullscreenMode)
                LayoutParams.WRAP_CONTENT
            else
                LayoutParams.MATCH_PARENT
            val inputArea = window.findViewById<View>(android.R.id.inputArea)
            ViewLayoutUtils.updateLayoutHeightOf(inputArea, layoutHeight)
            ViewLayoutUtils.updateLayoutGravityOf(inputArea, Gravity.BOTTOM)
            ViewLayoutUtils.updateLayoutHeightOf(mInputView!!, layoutHeight)
        }
    }

    /**
     * @param codePoints code points to get coordinates for.
     * @return x,y coordinates for this keyboard, as a flattened array.
     */
    fun getCoordinatesForCurrentKeyboard(codePoints: IntArray): IntArray {
        val keyboard = mKeyboardSwitcher.keyboard
                ?: return CoordinateUtils.newCoordinateArray(codePoints.size,
                        Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE)
        return keyboard.getCoordinates(codePoints)
    }

    // Callback for the {@link SuggestionStripView}, to call when the important notice strip is
    // pressed.
    override fun showImportantNoticeContents() {
        PermissionsManager.get(this).requestPermissions(
                this /* PermissionsResultCallback */, null, permission.READ_CONTACTS)/* activity */
    }

    override fun onRequestPermissionsResult(allGranted: Boolean) {
        ImportantNoticeUtils.updateContactsNoticeShown(this /* context */)
        setNeutralSuggestionStrip()
    }

    fun displaySettingsDialog() {
        if (isShowingOptionDialog) {
            return
        }
        showSubtypeSelectorAndSettings()
    }

    override fun onCustomRequest(requestCode: Int): Boolean {
        if (isShowingOptionDialog) return false
        when (requestCode) {
            Constants.CUSTOM_CODE_SHOW_INPUT_METHOD_PICKER -> {
                if (mRichImm?.hasMultipleEnabledIMEsOrSubtypes(true /* include aux subtypes */) == true) {
                    mRichImm?.inputMethodManager?.showInputMethodPicker()
                    return true
                }
                return false
            }
        }
        return false
    }

    fun switchLanguage(subtype: InputMethodSubtype) {
        val token = window.window!!.attributes.token
        mRichImm?.setInputMethodAndSubtype(token, subtype)
    }

    // TODO: Revise the language switch key behavior to make it much smarter and more reasonable.
    fun switchToNextSubtype() {
        val token = window.window!!.attributes.token
        mRichImm?.let {
            if (shouldSwitchToOtherInputMethods()) {
                it.switchToNextInputMethod(token, false /* onlyCurrentIme */)
                return
            }
            mSubtypeState.switchSubtype(token, it)
        }
    }

    // TODO: Instead of checking for alphabetic keyboard here, separate keycodes for
    // alphabetic shift and shift while in symbol layout and get rid of this method.
    private fun getCodePointForKeyboard(codePoint: Int): Int {
        if (Constants.CODE_SHIFT == codePoint) {
            val currentKeyboard = mKeyboardSwitcher.keyboard
            return if (null != currentKeyboard && currentKeyboard.mId.isAlphabetKeyboard) {
                codePoint
            } else Constants.CODE_SYMBOL_SHIFT
        }
        return codePoint
    }

    // Implementation of {@link KeyboardActionListener}.
    override fun onCodeInput(codePoint: Int,
                             x: Int,
                             y: Int,
                             isKeyRepeat: Boolean,
                             isDeadKey: Boolean) {
        // TODO: this processing does not belong inside LatinIME, the caller should be doing this.
        val mainKeyboardView = mKeyboardSwitcher.mainKeyboardView ?: return
        // x and y include some padding, but everything down the line (especially native
        // code) needs the coordinates in the keyboard frame.
        // TODO: We should reconsider which coordinate system should be used to represent
        // keyboard event. Also we should pull this up -- LatinIME has no business doing
        // this transformation, it should be done already before calling onEvent.
        val realCodePoint = getCodePointForKeyboard(codePoint)

        val keyX = mainKeyboardView.getKeyX(x)
        val keyY = mainKeyboardView.getKeyY(y)
        val event = createSoftwareKeypressEvent(
                realCodePoint,
                keyX,
                keyY,
                isKeyRepeat,
                isDeadKey)
        onEvent(event)
    }

    // This method is public for testability of LatinIME, but also in the future it should
    // completely replace #onCodeInput.
    fun onEvent(event: Event) {
        if (Constants.CODE_SHORTCUT == event.mKeyCode) {
            mRichImm?.switchToShortcutIme(this)
        }
        val completeInputTransaction = mInputLogic.onCodeInput(mSettings.current, event,
                mKeyboardSwitcher.keyboardShiftMode,
                mKeyboardSwitcher.currentKeyboardScriptId, mHandler)
        updateStateAfterInputTransaction(completeInputTransaction)
        mKeyboardSwitcher.onEvent(event, currentAutoCapsState, currentRecapitalizeState)
    }

    // Called from PointerTracker through the KeyboardActionListener interface
    override fun onTextInput(rawText: String) {
        // TODO: have the keyboard pass the correct key code when we need it.
        val event = Event.createSoftwareTextEvent(rawText, Constants.CODE_OUTPUT_TEXT)
        val completeInputTransaction = mInputLogic.onTextInput(mSettings.current, event,
                mKeyboardSwitcher.keyboardShiftMode, mHandler)
        updateStateAfterInputTransaction(completeInputTransaction)
        mKeyboardSwitcher.onEvent(event, currentAutoCapsState, currentRecapitalizeState)
    }

    override fun onStartBatchInput() {
        mInputLogic.onStartBatchInput(mSettings.current, mKeyboardSwitcher, mHandler)
        mGestureConsumer.onGestureStarted(
                mRichImm?.currentSubtypeLocale,
                mKeyboardSwitcher.keyboard)
    }

    override fun onUpdateBatchInput(batchPointers: InputPointers) {
        mInputLogic.onUpdateBatchInput(batchPointers)
    }

    override fun onEndBatchInput(batchPointers: InputPointers) {
        mInputLogic.onEndBatchInput(batchPointers)
        mGestureConsumer.onGestureCompleted(batchPointers)
    }

    override fun onCancelBatchInput() {
        mInputLogic.onCancelBatchInput(mHandler)
        mGestureConsumer.onGestureCanceled()
    }

    /**
     * To be called after the InputLogic has gotten a chance to act on the suggested words by the
     * IME for the full gesture, possibly updating the TextView to reflect the first suggestion.
     *
     *
     * This method must be run on the UI Thread.
     * @param suggestedWords suggested words by the IME for the full gesture.
     */
    fun onTailBatchInputResultShown(suggestedWords: SuggestedWords) {
        mGestureConsumer.onImeSuggestionsProcessed(suggestedWords,
                mInputLogic.composingStart, mInputLogic.composingLength,
                mDictionaryFacilitator)
    }

    // This method must run on the UI Thread.
    internal fun showGesturePreviewAndSuggestionStrip(suggestedWords: SuggestedWords,
                                                      dismissGestureFloatingPreviewText: Boolean) {
        showSuggestionStrip(suggestedWords)
        val mainKeyboardView = mKeyboardSwitcher.mainKeyboardView
        mainKeyboardView?.showGestureFloatingPreviewText(suggestedWords,
                dismissGestureFloatingPreviewText /* dismissDelayed */)
    }

    // Called from PointerTracker through the KeyboardActionListener interface
    override fun onFinishSlidingInput() {
        // User finished sliding input.
        mKeyboardSwitcher.onFinishSlidingInput(currentAutoCapsState,
                currentRecapitalizeState)
    }

    // Called from PointerTracker through the KeyboardActionListener interface
    override fun onCancelInput() {
        // User released a finger outside any key
        // Nothing to do so far.
    }

    fun hasSuggestionStripView(): Boolean {
        return null != mSuggestionStripView
    }

    private fun setSuggestedWords(suggestedWords: SuggestedWords) {
        val currentSettingsValues = mSettings.current
        mInputLogic.setSuggestedWords(suggestedWords)
        // TODO: Modify this when we support suggestions with hard keyboard

        Log.d("LatinIME", "suggestionStrip: ${hasSuggestionStripView()}")
        if (!hasSuggestionStripView()) {
            return
        }
        if (!onEvaluateInputViewShown()) {
            return
        }

        val shouldShowImportantNotice = ImportantNoticeUtils.shouldShowImportantNotice(this, currentSettingsValues)
        val shouldShowSuggestionCandidates = currentSettingsValues.mInputAttributes.mShouldShowSuggestions && currentSettingsValues.isSuggestionsEnabledPerUserSettings
        val shouldShowSuggestionsStripUnlessPassword = (shouldShowImportantNotice
                || currentSettingsValues.mShowsVoiceInputKey
                || shouldShowSuggestionCandidates
                || currentSettingsValues.isApplicationSpecifiedCompletionsOn)
        val shouldShowSuggestionsStrip = shouldShowSuggestionsStripUnlessPassword && !currentSettingsValues.mInputAttributes.mIsPasswordField
        mSuggestionStripView!!.updateVisibility(shouldShowSuggestionsStrip, isFullscreenMode)
        if (!shouldShowSuggestionsStrip) {
            return
        }

        val isEmptyApplicationSpecifiedCompletions = currentSettingsValues.isApplicationSpecifiedCompletionsOn && suggestedWords.isEmpty
        val noSuggestionsFromDictionaries = (suggestedWords.isEmpty
                || suggestedWords.isPunctuationSuggestions
                || isEmptyApplicationSpecifiedCompletions)
        val isBeginningOfSentencePrediction = suggestedWords.mInputStyle == SuggestedWords.INPUT_STYLE_BEGINNING_OF_SENTENCE_PREDICTION
        val noSuggestionsToOverrideImportantNotice = noSuggestionsFromDictionaries || isBeginningOfSentencePrediction
        if (shouldShowImportantNotice && noSuggestionsToOverrideImportantNotice) {
            if (mSuggestionStripView!!.maybeShowImportantNoticeTitle()) {
                return
            }
        }

        if (currentSettingsValues.isSuggestionsEnabledPerUserSettings
                || currentSettingsValues.isApplicationSpecifiedCompletionsOn
                // We should clear the contextual strip if there is no suggestion from dictionaries.
                || noSuggestionsFromDictionaries) {
            mSuggestionStripView!!.setSuggestions(suggestedWords,
                    mRichImm?.currentSubtype?.isRtlSubtype == true)
        }
    }

    // TODO[IL]: Move this out of LatinIME.
    fun getSuggestedWords(inputStyle: Int, sequenceNumber: Int,
                          callback: OnGetSuggestedWordsCallback) {
        val keyboard = mKeyboardSwitcher.keyboard
        if (keyboard == null) {
            callback.onGetSuggestedWords(SuggestedWords.emptyInstance)
            return
        }
        mInputLogic.getSuggestedWords(mSettings.current, keyboard,
                mKeyboardSwitcher.keyboardShiftMode, inputStyle, sequenceNumber, callback)
    }

    override fun showSuggestionStrip(suggestedWords: SuggestedWords) {
        if (suggestedWords.isEmpty) {
            setNeutralSuggestionStrip()
        } else {
            setSuggestedWords(suggestedWords)
        }
        // Cache the auto-correction in accessibility code so we can speak it if the user
        // touches a key that will insert it.
        AccessibilityUtils.getInstance().setAutoCorrection(suggestedWords)
    }

    // Called from {@link SuggestionStripView} through the {@link SuggestionStripView#Listener}
    // interface
    override fun pickSuggestionManually(suggestionInfo: SuggestedWordInfo) {
        val completeInputTransaction = mInputLogic.onPickSuggestionManually(
                mSettings.current, suggestionInfo,
                mKeyboardSwitcher.keyboardShiftMode,
                mKeyboardSwitcher.currentKeyboardScriptId,
                mHandler)
        updateStateAfterInputTransaction(completeInputTransaction)
    }

    // This will show either an empty suggestion strip (if prediction is enabled) or
    // punctuation suggestions (if it's disabled).
    override fun setNeutralSuggestionStrip() {
        val currentSettings = mSettings.current
        val neutralSuggestions = if (currentSettings.mBigramPredictionEnabled)
            SuggestedWords.emptyInstance
        else
            currentSettings.mSpacingAndPunctuations.mSuggestPuncList
        setSuggestedWords(neutralSuggestions)
    }

    // Outside LatinIME, only used by the {@link InputTestsBase} test suite.
    @UsedForTesting
    internal fun loadKeyboard() {
        // Since we are switching languages, the most urgent thing is to let the keyboard graphics
        // update. LoadKeyboard does that, but we need to wait for buffer flip for it to be on
        // the screen. Anything we do right now will delay this, so wait until the next frame
        // before we do the rest, like reopening dictionaries and updating suggestions. So we
        // post a message.
        mHandler.postReopenDictionaries()
        loadSettings()
        if (mKeyboardSwitcher.mainKeyboardView != null) {
            // Reload keyboard because the current language has been changed.
            mKeyboardSwitcher.loadKeyboard(currentInputEditorInfo, mSettings.current,
                    currentAutoCapsState, currentRecapitalizeState)
        }
    }

    /**
     * After an input transaction has been executed, some state must be updated. This includes
     * the shift state of the keyboard and suggestions. This method looks at the finished
     * inputTransaction to find out what is necessary and updates the state accordingly.
     * @param inputTransaction The transaction that has been executed.
     */
    private fun updateStateAfterInputTransaction(inputTransaction: InputTransaction) {
        when (inputTransaction.requiredShiftUpdate) {
            InputTransaction.SHIFT_UPDATE_LATER -> mHandler.postUpdateShiftState()
            InputTransaction.SHIFT_UPDATE_NOW -> mKeyboardSwitcher.requestUpdatingShiftState(currentAutoCapsState,
                    currentRecapitalizeState)
        }// SHIFT_NO_UPDATE
        if (inputTransaction.requiresUpdateSuggestions()) {
            val inputStyle: Int
            if (inputTransaction.mEvent.isSuggestionStripPress) {
                // Suggestion strip press: no input.
                inputStyle = SuggestedWords.INPUT_STYLE_NONE
            } else if (inputTransaction.mEvent.isGesture) {
                inputStyle = SuggestedWords.INPUT_STYLE_TAIL_BATCH
            } else {
                inputStyle = SuggestedWords.INPUT_STYLE_TYPING
            }
            mHandler.postUpdateSuggestionStrip(inputStyle)
        }
        if (inputTransaction.didAffectContents()) {
            mSubtypeState.setCurrentSubtypeHasBeenUsed()
        }
    }

    private fun hapticAndAudioFeedback(code: Int, repeatCount: Int) {
        val keyboardView = mKeyboardSwitcher.mainKeyboardView
        if (keyboardView != null && keyboardView.isInDraggingFinger) {
            // No need to feedback while finger is dragging.
            return
        }
        if (repeatCount > 0) {
            if (code == Constants.CODE_DELETE && !mInputLogic.mConnection.canDeleteCharacters()) {
                // No need to feedback when repeat delete key will have no effect.
                return
            }
            // TODO: Use event time that the last feedback has been generated instead of relying on
            // a repeat count to thin out feedback.
            if (repeatCount % PERIOD_FOR_AUDIO_AND_HAPTIC_FEEDBACK_IN_KEY_REPEAT == 0) {
                return
            }
        }
        val feedbackManager = AudioAndHapticFeedbackManager.instance
        if (repeatCount == 0) {
            // TODO: Reconsider how to perform haptic feedback when repeating key.
            feedbackManager.performHapticFeedback(keyboardView)
        }
        feedbackManager.performAudioFeedback(code)
    }

    // Callback of the {@link KeyboardActionListener}. This is called when a key is depressed;
    // release matching call is {@link #onReleaseKey(int,boolean)} below.
    override fun onPressKey(primaryCode: Int, repeatCount: Int,
                            isSinglePointer: Boolean) {
        mKeyboardSwitcher.onPressKey(primaryCode, isSinglePointer, currentAutoCapsState,
                currentRecapitalizeState)
        hapticAndAudioFeedback(primaryCode, repeatCount)
    }

    // Callback of the {@link KeyboardActionListener}. This is called when a key is released;
    // press matching call is {@link #onPressKey(int,int,boolean)} above.
    override fun onReleaseKey(primaryCode: Int, withSliding: Boolean) {
        mKeyboardSwitcher.onReleaseKey(primaryCode, withSliding, currentAutoCapsState,
                currentRecapitalizeState)
    }

    private fun getHardwareKeyEventDecoder(deviceId: Int): HardwareEventDecoder {
        val decoder = mHardwareEventDecoders.get(deviceId)
        if (null != decoder) return decoder
        // TODO: create the decoder according to the specification
        val newDecoder = HardwareKeyboardEventDecoder(deviceId)
        mHardwareEventDecoders.put(deviceId, newDecoder)
        return newDecoder
    }

    // Hooks for hardware keyboard
    override fun onKeyDown(keyCode: Int, keyEvent: KeyEvent): Boolean {
        if (mEmojiAltPhysicalKeyDetector == null) {
            mEmojiAltPhysicalKeyDetector = EmojiAltPhysicalKeyDetector(
                    applicationContext.resources)
        }
        mEmojiAltPhysicalKeyDetector!!.onKeyDown(keyEvent)
        if (!ProductionFlags.IS_HARDWARE_KEYBOARD_SUPPORTED) {
            return super.onKeyDown(keyCode, keyEvent)
        }
        val event = getHardwareKeyEventDecoder(
                keyEvent.deviceId).decodeHardwareKey(keyEvent)
        // If the event is not handled by LatinIME, we just pass it to the parent implementation.
        // If it's handled, we return true because we did handle it.
        if (event.isHandled) {
            mInputLogic.onCodeInput(mSettings.current, event,
                    mKeyboardSwitcher.keyboardShiftMode,
                    // TODO: this is not necessarily correct for a hardware keyboard right now
                    mKeyboardSwitcher.currentKeyboardScriptId,
                    mHandler)
            return true
        }
        return super.onKeyDown(keyCode, keyEvent)
    }

    override fun onKeyUp(keyCode: Int, keyEvent: KeyEvent): Boolean {
        if (mEmojiAltPhysicalKeyDetector == null) {
            mEmojiAltPhysicalKeyDetector = EmojiAltPhysicalKeyDetector(
                    applicationContext.resources)
        }
        mEmojiAltPhysicalKeyDetector!!.onKeyUp(keyEvent)
        if (!ProductionFlags.IS_HARDWARE_KEYBOARD_SUPPORTED) {
            return super.onKeyUp(keyCode, keyEvent)
        }
        val keyIdentifier = (keyEvent.deviceId shl 32 + keyEvent.keyCode).toLong()
        return if (mInputLogic.mCurrentlyPressedHardwareKeys.remove(keyIdentifier)) {
            true
        } else super.onKeyUp(keyCode, keyEvent)
    }

    internal fun launchSettings(extraEntryValue: String) {
        mInputLogic.commitTyped(mSettings.current, LastComposedWord.NOT_A_SEPARATOR)
        requestHideSelf(0)
        val mainKeyboardView = mKeyboardSwitcher.mainKeyboardView
        mainKeyboardView?.closing()
        val intent = Intent()
        intent.setClass(this@LatinIME, SettingsActivity::class.java)
        intent.flags = (Intent.FLAG_ACTIVITY_NEW_TASK
                or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        intent.putExtra(SettingsActivity.EXTRA_SHOW_HOME_AS_UP, false)
        intent.putExtra(SettingsActivity.EXTRA_ENTRY_KEY, extraEntryValue)
        startActivity(intent)
    }

    internal fun launchPersonalDictionary() {
        mInputLogic.commitTyped(mSettings.current, LastComposedWord.NOT_A_SEPARATOR)
        requestHideSelf(0)
        val mainKeyboardView = mKeyboardSwitcher.mainKeyboardView
        mainKeyboardView?.closing()

        val intent = Intent()
        intent.setClass(this@LatinIME, PersonalDictionaryActivity::class.java)
        intent.flags = (Intent.FLAG_ACTIVITY_NEW_TASK
                or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
    }

    private fun showSubtypeSelectorAndSettings() {
        val title = getString(R.string.english_ime_input_options)
        // TODO: Should use new string "Select active input modes".
        val languageSelectionTitle = getString(R.string.language_selection_title)
        val personalDictionary = getString(R.string.personal_dictionary_title)

        val pkgInfo = packageManager.getPackageInfo(packageName, 0)
        val appName = String.format("%s v%s (build %s)", getString(R.string.english_ime_name),
                pkgInfo.versionName, pkgInfo.versionCode)

        val items = arrayOf<CharSequence>(languageSelectionTitle, personalDictionary, appName)

        val imeId = mRichImm?.inputMethodIdOfThisIme
        val listener = OnClickListener { di, position ->
            di.dismiss()
            when (position) {
                0 -> {
                    val intent = IntentUtils.getInputLanguageSelectionIntent(
                            imeId,
                            Intent.FLAG_ACTIVITY_NEW_TASK
                                    or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                                    or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    intent.putExtra(Intent.EXTRA_TITLE, languageSelectionTitle)
                    startActivity(intent)
                }
                1 -> launchPersonalDictionary()
                2 -> launchSettings(SettingsActivity.EXTRA_ENTRY_VALUE_LONG_PRESS_COMMA)
            }
        }
        val builder = AlertDialog.Builder(
                DialogUtils.getPlatformDialogThemeContext(this))
        builder.setItems(items, listener).setTitle(title)
        val dialog = builder.create()
        dialog.setCancelable(true /* cancelable */)
        dialog.setCanceledOnTouchOutside(true /* cancelable */)
        showOptionDialog(dialog)
    }

    // TODO: Move this method out of {@link LatinIME}.
    private fun showOptionDialog(dialog: AlertDialog) {
        val windowToken = mKeyboardSwitcher.mainKeyboardView?.windowToken ?: return

        val window = dialog.window
        val lp = window!!.attributes
        lp.token = windowToken
        lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG
        window.attributes = lp
        window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)

        mOptionsDialog = dialog
        dialog.show()
    }

    fun dumpDictionaryForDebug(dictName: String) {
        if (mDictionaryFacilitator.isActive == false) {
            resetDictionaryFacilitatorIfNecessary()
        }
        mDictionaryFacilitator.dumpDictionaryForDebug(dictName)
    }

    fun debugDumpStateAndCrashWithException(context: String) {
        val settingsValues = mSettings.current
        val s = StringBuilder(settingsValues.toString())
        s.append("\nAttributes : ").append(settingsValues.mInputAttributes)
                .append("\nContext : ").append(context)
        throw RuntimeException(s.toString())
    }

    override fun dump(fd: FileDescriptor, fout: PrintWriter, args: Array<String>) {
        super.dump(fd, fout, args)

        val p = PrintWriterPrinter(fout)
        p.println("LatinIME state :")
        p.println("  VersionCode = " + ApplicationUtils.getVersionCode(this))
        p.println("  VersionName = " + ApplicationUtils.getVersionName(this))
        val keyboard = mKeyboardSwitcher.keyboard
        val keyboardMode = keyboard?.mId?.mMode ?: -1
        p.println("  Keyboard mode = $keyboardMode")
        val settingsValues = mSettings.current
        p.println(settingsValues.dump())
        p.println(mDictionaryFacilitator?.dump(this /* context */))
        // TODO: Dump all settings values
    }

    fun shouldSwitchToOtherInputMethods(): Boolean {
        // TODO: Revisit here to reorganize the settings. Probably we can/should use different
        // strategy once the implementation of
        // {@link InputMethodManager#shouldOfferSwitchingToNextInputMethod} is defined well.
        val fallbackValue = mSettings.current.mIncludesOtherImesInLanguageSwitchList
        val token = window.window!!.attributes.token ?: return fallbackValue
        return mRichImm?.shouldOfferSwitchingToNextInputMethod(token, fallbackValue) == true
    }

    fun shouldShowLanguageSwitchKey(): Boolean {
        // TODO: Revisit here to reorganize the settings. Probably we can/should use different
        // strategy once the implementation of
        // {@link InputMethodManager#shouldOfferSwitchingToNextInputMethod} is defined well.
        val fallbackValue = mSettings.current.isLanguageSwitchKeyEnabled
        val token = window.window!!.attributes.token ?: return fallbackValue
        return mRichImm?.shouldOfferSwitchingToNextInputMethod(token, fallbackValue) == true
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private fun setNavigationBarVisibility(visible: Boolean) {
        // For N and later, IMEs can specify Color.TRANSPARENT to make the navigation bar
        // transparent.  For other colors the system uses the default color.
        window.window!!.navigationBarColor = if (visible) Color.BLACK else Color.TRANSPARENT
    }

    companion object {
        internal val TAG = LatinIME::class.java.simpleName
        private val TRACE = false

        private val EXTENDED_TOUCHABLE_REGION_HEIGHT = 100
        private val PERIOD_FOR_AUDIO_AND_HAPTIC_FEEDBACK_IN_KEY_REPEAT = 2
        private val PENDING_IMS_CALLBACK_DURATION_MILLIS = 800
        internal val DELAY_WAIT_FOR_DICTIONARY_LOAD_MILLIS = TimeUnit.SECONDS.toMillis(2)
        internal val DELAY_DEALLOCATE_MEMORY_MILLIS = TimeUnit.SECONDS.toMillis(10)

        /**
         * The name of the scheme used by the Package Manager to warn of a new package installation,
         * replacement or removal.
         */
        private val SCHEME_PACKAGE = "package"

        // A helper method to split the code point and the key code. Ultimately, they should not be
        // squashed into the same variable, and this method should be removed.
        // public for testing, as we don't want to copy the same logic into test code
        fun createSoftwareKeypressEvent(keyCodeOrCodePoint: Int, keyX: Int,
                                        keyY: Int, isKeyRepeat: Boolean, isDeadKey: Boolean): Event {
            val keyCode: Int
            val codePoint: Int
            if (keyCodeOrCodePoint <= 0) {
                keyCode = keyCodeOrCodePoint
                codePoint = Event.NOT_A_CODE_POINT
            } else {
                keyCode = Event.NOT_A_KEY_CODE
                codePoint = keyCodeOrCodePoint
            }
            return Event.createSoftwareKeypressEvent(codePoint, keyCode, keyX, keyY, isKeyRepeat, isDeadKey)
        }
    }
}
