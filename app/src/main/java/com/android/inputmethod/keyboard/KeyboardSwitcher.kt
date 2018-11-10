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

package com.android.inputmethod.keyboard

import android.content.Context
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import com.android.inputmethod.event.Event
import com.android.inputmethod.keyboard.KeyboardLayoutSet.KeyboardLayoutSetException
import com.android.inputmethod.keyboard.emoji.EmojiPalettesView
import com.android.inputmethod.keyboard.internal.KeyboardState
import com.android.inputmethod.keyboard.internal.KeyboardTextsSet
import com.android.inputmethod.latin.*
import com.android.inputmethod.latin.define.ProductionFlags
import com.android.inputmethod.latin.settings.Settings
import com.android.inputmethod.latin.settings.SettingsValues
import com.android.inputmethod.latin.utils.*
import io.sentry.event.BreadcrumbBuilder

class KeyboardSwitcher private constructor()// Intentional empty constructor for singleton.
    : KeyboardState.SwitchActions {

    private lateinit var mCurrentInputView: InputView
    private lateinit var mMainKeyboardFrame: View
    var mainKeyboardView: MainKeyboardView? = null
        private set
    private lateinit var mEmojiPalettesView: EmojiPalettesView

    private lateinit var mLatinIME: LatinIME
    private lateinit var mRichImm: RichInputMethodManager
    private lateinit var mState: KeyboardState

    private var mIsHardwareAcceleratedDrawingEnabled: Boolean = false


    private var mKeyboardLayoutSet: KeyboardLayoutSet? = null
    // TODO: The following {@link KeyboardTextsSet} should be in {@link KeyboardLayoutSet}.
    private val mKeyboardTextsSet = KeyboardTextsSet()

    private var mKeyboardTheme: KeyboardTheme? = null
    private var mThemeContext: Context? = null

    val keyboard: Keyboard?
        get() = if (mainKeyboardView != null) {
            mainKeyboardView!!.keyboard
        } else null

    val keyboardSwitchState: KeyboardSwitchState
        get() {
            val hidden = !isShowingEmojiPalettes && (mKeyboardLayoutSet == null
                    || mainKeyboardView == null
                    || !mainKeyboardView!!.isShown)

            if (hidden) {
                return KeyboardSwitchState.HIDDEN
            } else if (isShowingEmojiPalettes) {
                return KeyboardSwitchState.EMOJI
            } else if (isShowingKeyboardId(KeyboardId.ELEMENT_SYMBOLS_SHIFTED)) {
                return KeyboardSwitchState.SYMBOLS_SHIFTED
            }
            return KeyboardSwitchState.OTHER
        }

    val isShowingEmojiPalettes: Boolean
        get() = mEmojiPalettesView.isShown

    val isShowingMoreKeysPanel: Boolean
        get() = if (isShowingEmojiPalettes) {
            false
        } else mainKeyboardView!!.isShowingMoreKeysPanel

    val visibleKeyboardView: View?
        get() = if (isShowingEmojiPalettes) {
            mEmojiPalettesView
        } else mainKeyboardView

    val keyboardShiftMode: Int
        get() {
            val keyboard = keyboard ?: return WordComposer.CAPS_MODE_OFF
            when (keyboard.mId.mElementId) {
                KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCKED, KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED -> return WordComposer.CAPS_MODE_MANUAL_SHIFT_LOCKED
                KeyboardId.ELEMENT_ALPHABET_MANUAL_SHIFTED -> return WordComposer.CAPS_MODE_MANUAL_SHIFTED
                KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED -> return WordComposer.CAPS_MODE_AUTO_SHIFTED
                else -> return WordComposer.CAPS_MODE_OFF
            }
        }

    val currentKeyboardScriptId: Int
        get() = if (null == mKeyboardLayoutSet) {
            ScriptUtils.SCRIPT_UNKNOWN
        } else mKeyboardLayoutSet!!.scriptId

    private fun initInternal(latinIme: LatinIME) {
        mLatinIME = latinIme
        mRichImm = RichInputMethodManager.getInstance()
        mState = KeyboardState(this)

        @Suppress("DEPRECATION")
        mIsHardwareAcceleratedDrawingEnabled = mLatinIME.enableHardwareAcceleration()
    }

    fun updateKeyboardTheme() {
        val themeUpdated = updateKeyboardThemeAndContextThemeWrapper(
                mLatinIME, KeyboardTheme.getKeyboardTheme(mLatinIME /* context */))
        if (themeUpdated && mainKeyboardView != null) {
            mLatinIME.setInputView(onCreateInputView(mIsHardwareAcceleratedDrawingEnabled))
        }
    }

    private fun updateKeyboardThemeAndContextThemeWrapper(context: Context,
                                                          keyboardTheme: KeyboardTheme?): Boolean {
        if (keyboardTheme == null) {
            return false
        }

        if (mThemeContext == null || keyboardTheme != mKeyboardTheme) {
            mKeyboardTheme = keyboardTheme
            mThemeContext = ContextThemeWrapper(context, keyboardTheme.mStyleId)
            KeyboardLayoutSet.onKeyboardThemeChanged()
            return true
        }
        return false
    }

    fun loadKeyboard(editorInfo: EditorInfo, settingsValues: SettingsValues,
                     currentAutoCapsState: Int, currentRecapitalizeState: Int) {
        val context = mThemeContext ?: return

        val builder = KeyboardLayoutSet.Builder(
                context, editorInfo)
        val res = context.resources
        val keyboardWidth = ResourceUtils.getDefaultKeyboardWidth(res)
        val keyboardHeight = ResourceUtils.getKeyboardHeight(res, settingsValues)
        builder.setKeyboardGeometry(keyboardWidth, keyboardHeight)
        builder.setSubtype(mRichImm.currentSubtype)
        builder.setVoiceInputKeyEnabled(settingsValues.mShowsVoiceInputKey)
        builder.setLanguageSwitchKeyEnabled(mLatinIME.shouldShowLanguageSwitchKey())
        builder.setSplitLayoutEnabledByUser(ProductionFlags.IS_SPLIT_KEYBOARD_SUPPORTED && settingsValues.mIsSplitKeyboardEnabled)
        mKeyboardLayoutSet = builder.build()
        try {
            mState.onLoadKeyboard(currentAutoCapsState, currentRecapitalizeState)
            mKeyboardTextsSet.setLocale(mRichImm.currentSubtypeLocale, mThemeContext!!)
        } catch (e: KeyboardLayoutSetException) {
            Log.w(TAG, "loading keyboard failed: " + e.mKeyboardId, e.cause)
        }

        ExceptionLogger.sentry.recordBreadcrumb(
                BreadcrumbBuilder()
                        .setMessage("Current subtype: " + mRichImm.currentSubtype.toString())
                        .build()
        )
    }

    fun saveKeyboardState() {
        if (keyboard != null || isShowingEmojiPalettes) {
            mState.onSaveKeyboardState()
        }
    }

    fun onHideWindow() {
        if (mainKeyboardView != null) {
            mainKeyboardView!!.onHideWindow()
        }
    }

    private fun setKeyboard(
            keyboardId: Int,
            toggleState: KeyboardSwitchState) {
        // Make {@link MainKeyboardView} visible and hide {@link EmojiPalettesView}.
        val currentSettingsValues = Settings.getInstance().current
        setMainKeyboardFrame(currentSettingsValues, toggleState)
        // TODO: pass this object to setKeyboard instead of getting the current values.
        val keyboardView = mainKeyboardView
        val oldKeyboard = keyboardView!!.keyboard
        val newKeyboard = mKeyboardLayoutSet!!.getKeyboard(keyboardId)
        keyboardView.setKeyboard(newKeyboard)
        mCurrentInputView.setKeyboardTopPadding(newKeyboard.mTopPadding)
        keyboardView.setKeyPreviewPopupEnabled(
                currentSettingsValues.mKeyPreviewPopupOn,
                currentSettingsValues.mKeyPreviewPopupDismissDelay)
        keyboardView.setKeyPreviewAnimationParams(
                currentSettingsValues.mHasCustomKeyPreviewAnimationParams,
                currentSettingsValues.mKeyPreviewShowUpStartXScale,
                currentSettingsValues.mKeyPreviewShowUpStartYScale,
                currentSettingsValues.mKeyPreviewShowUpDuration,
                currentSettingsValues.mKeyPreviewDismissEndXScale,
                currentSettingsValues.mKeyPreviewDismissEndYScale,
                currentSettingsValues.mKeyPreviewDismissDuration)
        keyboardView.updateShortcutKey(mRichImm.isShortcutImeReady)
        val subtypeChanged = oldKeyboard == null || newKeyboard.mId.mSubtype != oldKeyboard.mId.mSubtype
        val languageOnSpacebarFormatType = LanguageOnSpacebarUtils
                .getLanguageOnSpacebarFormatType(newKeyboard.mId.mSubtype!!)
        val hasMultipleEnabledIMEsOrSubtypes = mRichImm
                .hasMultipleEnabledIMEsOrSubtypes(true /* shouldIncludeAuxiliarySubtypes */)
        keyboardView.startDisplayLanguageOnSpacebar(subtypeChanged, languageOnSpacebarFormatType,
                hasMultipleEnabledIMEsOrSubtypes)
    }

    // TODO: Remove this method. Come up with a more comprehensive way to reset the keyboard layout
    // when a keyboard layout set doesn't get reloaded in LatinIME.onStartInputViewInternal().
    fun resetKeyboardStateToAlphabet(currentAutoCapsState: Int,
                                     currentRecapitalizeState: Int) {
        mState.onResetKeyboardStateToAlphabet(currentAutoCapsState, currentRecapitalizeState)
    }

    fun onPressKey(code: Int, isSinglePointer: Boolean,
                   currentAutoCapsState: Int, currentRecapitalizeState: Int) {
        mState.onPressKey(code, isSinglePointer, currentAutoCapsState, currentRecapitalizeState)
    }

    fun onReleaseKey(code: Int, withSliding: Boolean,
                     currentAutoCapsState: Int, currentRecapitalizeState: Int) {
        mState.onReleaseKey(code, withSliding, currentAutoCapsState, currentRecapitalizeState)
    }

    fun onFinishSlidingInput(currentAutoCapsState: Int,
                             currentRecapitalizeState: Int) {
        mState.onFinishSlidingInput(currentAutoCapsState, currentRecapitalizeState)
    }

    // Implements {@link KeyboardState.SwitchActions}.
    override fun setAlphabetKeyboard() {
        if (KeyboardState.SwitchActions.DEBUG_ACTION) {
            Log.d(TAG, "setAlphabetKeyboard")
        }
        setKeyboard(KeyboardId.ELEMENT_ALPHABET, KeyboardSwitchState.OTHER)
    }

    // Implements {@link KeyboardState.SwitchActions}.
    override fun setAlphabetManualShiftedKeyboard() {
        if (KeyboardState.SwitchActions.DEBUG_ACTION) {
            Log.d(TAG, "setAlphabetManualShiftedKeyboard")
        }
        setKeyboard(KeyboardId.ELEMENT_ALPHABET_MANUAL_SHIFTED, KeyboardSwitchState.OTHER)
    }

    // Implements {@link KeyboardState.SwitchActions}.
    override fun setAlphabetAutomaticShiftedKeyboard() {
        if (KeyboardState.SwitchActions.DEBUG_ACTION) {
            Log.d(TAG, "setAlphabetAutomaticShiftedKeyboard")
        }
        setKeyboard(KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED, KeyboardSwitchState.OTHER)
    }

    // Implements {@link KeyboardState.SwitchActions}.
    override fun setAlphabetShiftLockedKeyboard() {
        if (KeyboardState.SwitchActions.DEBUG_ACTION) {
            Log.d(TAG, "setAlphabetShiftLockedKeyboard")
        }
        setKeyboard(KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCKED, KeyboardSwitchState.OTHER)
    }

    // Implements {@link KeyboardState.SwitchActions}.
    override fun setAlphabetShiftLockShiftedKeyboard() {
        if (KeyboardState.SwitchActions.DEBUG_ACTION) {
            Log.d(TAG, "setAlphabetShiftLockShiftedKeyboard")
        }
        setKeyboard(KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED, KeyboardSwitchState.OTHER)
    }

    // Implements {@link KeyboardState.SwitchActions}.
    override fun setSymbolsKeyboard() {
        if (KeyboardState.SwitchActions.DEBUG_ACTION) {
            Log.d(TAG, "setSymbolsKeyboard")
        }
        setKeyboard(KeyboardId.ELEMENT_SYMBOLS, KeyboardSwitchState.OTHER)
    }

    // Implements {@link KeyboardState.SwitchActions}.
    override fun setSymbolsShiftedKeyboard() {
        if (KeyboardState.SwitchActions.DEBUG_ACTION) {
            Log.d(TAG, "setSymbolsShiftedKeyboard")
        }
        setKeyboard(KeyboardId.ELEMENT_SYMBOLS_SHIFTED, KeyboardSwitchState.SYMBOLS_SHIFTED)
    }

    fun isImeSuppressedByHardwareKeyboard(
            settingsValues: SettingsValues,
            toggleState: KeyboardSwitchState): Boolean {
        return settingsValues.mHasHardwareKeyboard && toggleState == KeyboardSwitchState.HIDDEN
    }

    private fun setMainKeyboardFrame(
            settingsValues: SettingsValues,
            toggleState: KeyboardSwitchState) {
        val visibility = if (isImeSuppressedByHardwareKeyboard(settingsValues, toggleState))
            View.GONE
        else
            View.VISIBLE
        mainKeyboardView!!.visibility = visibility
        // The visibility of {@link #mKeyboardView} must be aligned with {@link #MainKeyboardFrame}.
        // @see #getVisibleKeyboardView() and
        // @see LatinIME#onComputeInset(android.inputmethodservice.InputMethodService.Insets)
        mMainKeyboardFrame.visibility = visibility
        mEmojiPalettesView.visibility = View.GONE
        mEmojiPalettesView.stopEmojiPalettes()
    }

    // Implements {@link KeyboardState.SwitchActions}.
    override fun setEmojiKeyboard() {
        if (KeyboardState.SwitchActions.DEBUG_ACTION) {
            Log.d(TAG, "setEmojiKeyboard")
        }
        val keyboard = mKeyboardLayoutSet!!.getKeyboard(KeyboardId.ELEMENT_ALPHABET)
        mMainKeyboardFrame.visibility = View.GONE
        // The visibility of {@link #mKeyboardView} must be aligned with {@link #MainKeyboardFrame}.
        // @see #getVisibleKeyboardView() and
        // @see LatinIME#onComputeInset(android.inputmethodservice.InputMethodService.Insets)
        mainKeyboardView!!.visibility = View.GONE
        mEmojiPalettesView.startEmojiPalettes(
                mKeyboardTextsSet.getText(KeyboardTextsSet.SWITCH_TO_ALPHA_KEY_LABEL),
                mainKeyboardView!!.keyVisualAttribute, keyboard.mIconsSet)
        mEmojiPalettesView.visibility = View.VISIBLE
    }

    enum class KeyboardSwitchState constructor(internal val mKeyboardId: Int) {
        HIDDEN(-1),
        SYMBOLS_SHIFTED(KeyboardId.ELEMENT_SYMBOLS_SHIFTED),
        EMOJI(KeyboardId.ELEMENT_EMOJI_RECENTS),
        OTHER(-1)
    }

    fun onToggleKeyboard(toggleState: KeyboardSwitchState) {
        val currentState = keyboardSwitchState
        Log.w(TAG, "onToggleKeyboard() : Current = $currentState : Toggle = $toggleState")
        if (currentState == toggleState) {
            mLatinIME.stopShowingInputView()
            mLatinIME.hideWindow()
            setAlphabetKeyboard()
        } else {
            mLatinIME.startShowingInputView(true)
            if (toggleState == KeyboardSwitchState.EMOJI) {
                setEmojiKeyboard()
            } else {
                mEmojiPalettesView.stopEmojiPalettes()
                mEmojiPalettesView.visibility = View.GONE

                mMainKeyboardFrame.visibility = View.VISIBLE
                mainKeyboardView!!.visibility = View.VISIBLE
                setKeyboard(toggleState.mKeyboardId, toggleState)
            }
        }
    }

    // Future method for requesting an updating to the shift state.
    override fun requestUpdatingShiftState(autoCapsFlags: Int, recapitalizeMode: Int) {
        if (KeyboardState.SwitchActions.DEBUG_ACTION) {
            Log.d(TAG, "requestUpdatingShiftState: "
                    + " autoCapsFlags=" + CapsModeUtils.flagsToString(autoCapsFlags)
                    + " recapitalizeMode=" + RecapitalizeStatus.modeToString(recapitalizeMode))
        }
        mState.onUpdateShiftState(autoCapsFlags, recapitalizeMode)
    }

    // Implements {@link KeyboardState.SwitchActions}.
    override fun startDoubleTapShiftKeyTimer() {
        if (KeyboardState.SwitchActions.DEBUG_TIMER_ACTION) {
            Log.d(TAG, "startDoubleTapShiftKeyTimer")
        }
        val keyboardView = mainKeyboardView
        keyboardView?.startDoubleTapShiftKeyTimer()
    }

    // Implements {@link KeyboardState.SwitchActions}.
    override fun cancelDoubleTapShiftKeyTimer() {
        if (KeyboardState.SwitchActions.DEBUG_TIMER_ACTION) {
            Log.d(TAG, "setAlphabetKeyboard")
        }
        val keyboardView = mainKeyboardView
        keyboardView?.cancelDoubleTapShiftKeyTimer()
    }

    // Implements {@link KeyboardState.SwitchActions}.
    override fun isInDoubleTapShiftKeyTimeout(): Boolean {
        if (KeyboardState.SwitchActions.DEBUG_TIMER_ACTION) {
            Log.d(TAG, "isInDoubleTapShiftKeyTimeout")
        }
        val keyboardView = mainKeyboardView
        return keyboardView != null && keyboardView.isInDoubleTapShiftKeyTimeout
    }

    /**
     * Updates state machine to figure out when to automatically switch back to the previous mode.
     */
    fun onEvent(event: Event, currentAutoCapsState: Int,
                currentRecapitalizeState: Int) {
        mState.onEvent(event, currentAutoCapsState, currentRecapitalizeState)
    }

    fun isShowingKeyboardId(vararg keyboardIds: Int): Boolean {
        if (mainKeyboardView == null || !mainKeyboardView!!.isShown) {
            return false
        }
        val activeKeyboardId = mainKeyboardView!!.keyboard!!.mId.mElementId
        for (keyboardId in keyboardIds) {
            if (activeKeyboardId == keyboardId) {
                return true
            }
        }
        return false
    }

    fun deallocateMemory() {
        mainKeyboardView?.let {
            it.cancelAllOngoingEvents()
            it.deallocateMemory()
        }

        mEmojiPalettesView.stopEmojiPalettes()
    }

    fun onCreateInputView(isHardwareAcceleratedDrawingEnabled: Boolean): View {
        mainKeyboardView?.closing()

        KeyboardTheme.getKeyboardTheme(mLatinIME)?.let {
            updateKeyboardThemeAndContextThemeWrapper(mLatinIME, it)
        }

        val currentInputView = LayoutInflater.from(mThemeContext)
                .inflate(R.layout.input_view, null) as InputView
        mCurrentInputView = currentInputView
        mMainKeyboardFrame = currentInputView.findViewById(R.id.main_keyboard_frame)

        val keyboardView = currentInputView.findViewById<View>(R.id.keyboard_view) as MainKeyboardView
        keyboardView.setHardwareAcceleratedDrawingEnabled(isHardwareAcceleratedDrawingEnabled)
        keyboardView.setKeyboardActionListener(mLatinIME)
        mainKeyboardView = keyboardView

        val emojiPalettesView = currentInputView.findViewById<View>(
                R.id.emoji_palettes_view) as EmojiPalettesView
        emojiPalettesView.setHardwareAcceleratedDrawingEnabled(
                isHardwareAcceleratedDrawingEnabled)
        emojiPalettesView.setKeyboardActionListener(mLatinIME)
        mEmojiPalettesView = emojiPalettesView

        return currentInputView
    }

    companion object {
        private val TAG = KeyboardSwitcher::class.java.simpleName

        val instance = KeyboardSwitcher()

        fun init(latinIme: LatinIME) {
            instance.initInternal(latinIme)
        }
    }
}
