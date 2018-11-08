/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.inputmethod.latin.settings

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.util.Log
import android.view.inputmethod.EditorInfo

import com.android.inputmethod.latin.InputAttributes
import com.android.inputmethod.latin.R
import com.android.inputmethod.latin.RichInputMethodManager
import com.android.inputmethod.latin.utils.ResourceUtils

import java.util.Arrays
import java.util.Locale

/**
 * When you call the constructor of this class, you may want to change the current system locale by
 * using [com.android.inputmethod.latin.utils.RunInLocale].
 */
class SettingsValues(context: Context, prefs: SharedPreferences, res: Resources,
        // From the input box
        val mInputAttributes: InputAttributes) {

    // From resources:
    val mSpacingAndPunctuations: SpacingAndPunctuations
    val mDelayInMillisecondsToUpdateOldSuggestions: Int
    val mDoubleSpacePeriodTimeout: Long
    // From configuration:
    val mLocale: Locale
    val mHasHardwareKeyboard: Boolean
    val mDisplayOrientation: Int
    // From preferences, in the same order as xml/prefs.xml:
    val mAutoCap: Boolean
    val mVibrateOn: Boolean
    val mSoundOn: Boolean
    val mKeyPreviewPopupOn: Boolean
    val mShowsVoiceInputKey: Boolean
    val mIncludesOtherImesInLanguageSwitchList: Boolean
    val mShowsLanguageSwitchKey: Boolean
    val mUseContactsDict: Boolean
    val isPersonalizationEnabled: Boolean
    val mUseDoubleSpacePeriod: Boolean
    val mBlockPotentiallyOffensive: Boolean
    // Use bigrams to predict the next word when there is no input for it yet
    val mBigramPredictionEnabled: Boolean
    val mGestureInputEnabled: Boolean
    val mGestureTrailEnabled: Boolean
    val mGestureFloatingPreviewTextEnabled: Boolean
    val mSlidingKeyInputPreviewEnabled: Boolean
    val mKeyLongpressTimeout: Int
    val mEnableEmojiAltPhysicalKey: Boolean
    val mShowAppIcon: Boolean
    val mIsShowAppIconSettingInPreferences: Boolean
    val mCloudSyncEnabled: Boolean
    val isMetricsLoggingEnabled: Boolean
    val mShouldShowLxxSuggestionUi: Boolean
    // Use split layout for keyboard.
    val mIsSplitKeyboardEnabled: Boolean
    val mScreenMetrics: Int

    // Deduced settings
    val mKeypressVibrationDuration: Int
    val mKeypressSoundVolume: Float
    val mKeyPreviewPopupDismissDelay: Int
    private val mAutoCorrectEnabled: Boolean
    val mAutoCorrectionThreshold: Float
    val mPlausibilityThreshold: Float
    val mAutoCorrectionEnabledPerUserSettings: Boolean
    val isSuggestionsEnabledPerUserSettings: Boolean

    // Debug settings
    val mIsInternal: Boolean
    val mHasCustomKeyPreviewAnimationParams: Boolean
    val mHasKeyboardResize: Boolean
    val mKeyboardHeightScale: Float
    val mKeyPreviewShowUpDuration: Int
    val mKeyPreviewDismissDuration: Int
    val mKeyPreviewShowUpStartXScale: Float
    val mKeyPreviewShowUpStartYScale: Float
    val mKeyPreviewDismissEndXScale: Float
    val mKeyPreviewDismissEndYScale: Float

    val mAccount: String?

    val isApplicationSpecifiedCompletionsOn: Boolean
        get() = mInputAttributes.mApplicationSpecifiedCompletionOn

    /* include aux subtypes *//* include aux subtypes */ val isLanguageSwitchKeyEnabled: Boolean
        get() {
            if (!mShowsLanguageSwitchKey) {
                return false
            }
            val imm = RichInputMethodManager.getInstance()
            return if (mIncludesOtherImesInLanguageSwitchList) {
                imm.hasMultipleEnabledIMEsOrSubtypes(false)
            } else imm.hasMultipleEnabledSubtypesInThisIme(false)
        }

    init {
        mLocale = res.configuration.locale
        // Get the resources
        mDelayInMillisecondsToUpdateOldSuggestions = res.getInteger(R.integer.config_delay_in_milliseconds_to_update_old_suggestions)
        mSpacingAndPunctuations = SpacingAndPunctuations(res)

        // Get the settings preferences
        mAutoCap = prefs.getBoolean(Settings.PREF_AUTO_CAP, true)
        mVibrateOn = Settings.readVibrationEnabled(prefs, res)
        mSoundOn = Settings.readKeypressSoundEnabled(prefs, res)
        mKeyPreviewPopupOn = Settings.readKeyPreviewPopupEnabled(prefs, res)
        mSlidingKeyInputPreviewEnabled = prefs.getBoolean(
                DebugSettings.PREF_SLIDING_KEY_INPUT_PREVIEW, true)
        mShowsVoiceInputKey = (needsToShowVoiceInputKey(prefs, res)
                && mInputAttributes.mShouldShowVoiceInputKey)
        mIncludesOtherImesInLanguageSwitchList = true
        mShowsLanguageSwitchKey = true
        mUseContactsDict = prefs.getBoolean(Settings.PREF_KEY_USE_CONTACTS_DICT, true)
        isPersonalizationEnabled = prefs.getBoolean(Settings.PREF_KEY_USE_PERSONALIZED_DICTS, true)
        mUseDoubleSpacePeriod = prefs.getBoolean(Settings.PREF_KEY_USE_DOUBLE_SPACE_PERIOD, true) && mInputAttributes.mIsGeneralTextInput
        mBlockPotentiallyOffensive = Settings.readBlockPotentiallyOffensive(prefs, res)
        mAutoCorrectEnabled = Settings.readAutoCorrectEnabled(prefs, res)
        val autoCorrectionThresholdRawValue = if (mAutoCorrectEnabled)
            res.getString(R.string.auto_correction_threshold_mode_index_modest)
        else
            res.getString(R.string.auto_correction_threshold_mode_index_off)
        mBigramPredictionEnabled = readBigramPredictionEnabled(prefs, res)
        mDoubleSpacePeriodTimeout = res.getInteger(R.integer.config_double_space_period_timeout).toLong()
        mHasHardwareKeyboard = Settings.readHasHardwareKeyboard(res.configuration)
        isMetricsLoggingEnabled = prefs.getBoolean(Settings.PREF_ENABLE_METRICS_LOGGING, true)
        mIsSplitKeyboardEnabled = prefs.getBoolean(Settings.PREF_ENABLE_SPLIT_KEYBOARD, false)
        mScreenMetrics = Settings.readScreenMetrics(res)

        mShouldShowLxxSuggestionUi = Settings.SHOULD_SHOW_LXX_SUGGESTION_UI && prefs.getBoolean(DebugSettings.PREF_SHOULD_SHOW_LXX_SUGGESTION_UI, true)
        // Compute other readable settings
        mKeyLongpressTimeout = Settings.readKeyLongpressTimeout(prefs, res)
        mKeypressVibrationDuration = Settings.readKeypressVibrationDuration(prefs, res)
        mKeypressSoundVolume = Settings.readKeypressSoundVolume(prefs, res)
        mKeyPreviewPopupDismissDelay = Settings.readKeyPreviewPopupDismissDelay(prefs, res)
        mEnableEmojiAltPhysicalKey = prefs.getBoolean(
                Settings.PREF_ENABLE_EMOJI_ALT_PHYSICAL_KEY, true)
        mShowAppIcon = Settings.readShowSetupWizardIcon(prefs, context)
        mIsShowAppIconSettingInPreferences = prefs.contains(Settings.PREF_SHOW_SETUP_WIZARD_ICON)
        mAutoCorrectionThreshold = readAutoCorrectionThreshold(res,
                autoCorrectionThresholdRawValue)
        mPlausibilityThreshold = Settings.readPlausibilityThreshold(res)
        mGestureInputEnabled = Settings.readGestureInputEnabled(prefs, res)
        mGestureTrailEnabled = prefs.getBoolean(Settings.PREF_GESTURE_PREVIEW_TRAIL, true)
        mCloudSyncEnabled = prefs.getBoolean(LocalSettingsConstants.PREF_ENABLE_CLOUD_SYNC, false)
        mAccount = prefs.getString(LocalSettingsConstants.PREF_ACCOUNT_NAME, null/* default */)
        mGestureFloatingPreviewTextEnabled = !mInputAttributes.mDisableGestureFloatingPreviewText && prefs.getBoolean(Settings.PREF_GESTURE_FLOATING_PREVIEW_TEXT, true)
        mAutoCorrectionEnabledPerUserSettings = mAutoCorrectEnabled && !mInputAttributes.mInputTypeNoAutoCorrect
        isSuggestionsEnabledPerUserSettings = readSuggestionsEnabled(prefs)
        mIsInternal = Settings.isInternal(prefs)
        mHasCustomKeyPreviewAnimationParams = prefs.getBoolean(
                DebugSettings.PREF_HAS_CUSTOM_KEY_PREVIEW_ANIMATION_PARAMS, false)
        mHasKeyboardResize = prefs.getBoolean(DebugSettings.PREF_RESIZE_KEYBOARD, false)
        mKeyboardHeightScale = Settings.readKeyboardHeight(prefs, DEFAULT_SIZE_SCALE)
        mKeyPreviewShowUpDuration = Settings.readKeyPreviewAnimationDuration(
                prefs, DebugSettings.PREF_KEY_PREVIEW_SHOW_UP_DURATION,
                res.getInteger(R.integer.config_key_preview_show_up_duration))
        mKeyPreviewDismissDuration = Settings.readKeyPreviewAnimationDuration(
                prefs, DebugSettings.PREF_KEY_PREVIEW_DISMISS_DURATION,
                res.getInteger(R.integer.config_key_preview_dismiss_duration))
        val defaultKeyPreviewShowUpStartScale = ResourceUtils.getFloatFromFraction(
                res, R.fraction.config_key_preview_show_up_start_scale)
        val defaultKeyPreviewDismissEndScale = ResourceUtils.getFloatFromFraction(
                res, R.fraction.config_key_preview_dismiss_end_scale)
        mKeyPreviewShowUpStartXScale = Settings.readKeyPreviewAnimationScale(
                prefs, DebugSettings.PREF_KEY_PREVIEW_SHOW_UP_START_X_SCALE,
                defaultKeyPreviewShowUpStartScale)
        mKeyPreviewShowUpStartYScale = Settings.readKeyPreviewAnimationScale(
                prefs, DebugSettings.PREF_KEY_PREVIEW_SHOW_UP_START_Y_SCALE,
                defaultKeyPreviewShowUpStartScale)
        mKeyPreviewDismissEndXScale = Settings.readKeyPreviewAnimationScale(
                prefs, DebugSettings.PREF_KEY_PREVIEW_DISMISS_END_X_SCALE,
                defaultKeyPreviewDismissEndScale)
        mKeyPreviewDismissEndYScale = Settings.readKeyPreviewAnimationScale(
                prefs, DebugSettings.PREF_KEY_PREVIEW_DISMISS_END_Y_SCALE,
                defaultKeyPreviewDismissEndScale)
        mDisplayOrientation = res.configuration.orientation
    }// Store the input attributes

    fun needsToLookupSuggestions(): Boolean {
        return mInputAttributes.mShouldShowSuggestions && (mAutoCorrectionEnabledPerUserSettings || isSuggestionsEnabledPerUserSettings)
    }

    fun isWordSeparator(code: Int): Boolean {
        return mSpacingAndPunctuations.isWordSeparator(code)
    }

    fun isWordConnector(code: Int): Boolean {
        return mSpacingAndPunctuations.isWordConnector(code)
    }

    fun isWordCodePoint(code: Int): Boolean {
        return (Character.isLetter(code) || isWordConnector(code)
                || Character.COMBINING_SPACING_MARK.toInt() == Character.getType(code))
    }

    fun isUsuallyPrecededBySpace(code: Int): Boolean {
        return mSpacingAndPunctuations.isUsuallyPrecededBySpace(code)
    }

    fun isUsuallyFollowedBySpace(code: Int): Boolean {
        return mSpacingAndPunctuations.isUsuallyFollowedBySpace(code)
    }

    fun shouldInsertSpacesAutomatically(): Boolean {
        return mInputAttributes.mShouldInsertSpacesAutomatically
    }

    fun isSameInputType(editorInfo: EditorInfo): Boolean {
        return mInputAttributes.isSameInputType(editorInfo)
    }

    fun hasSameOrientation(configuration: Configuration): Boolean {
        return mDisplayOrientation == configuration.orientation
    }

    fun dump(): String {
        val sb = StringBuilder("Current settings :")
        sb.append("\n   mSpacingAndPunctuations = ")
        sb.append("" + mSpacingAndPunctuations.dump())
        sb.append("\n   mDelayInMillisecondsToUpdateOldSuggestions = ")
        sb.append("" + mDelayInMillisecondsToUpdateOldSuggestions)
        sb.append("\n   mAutoCap = ")
        sb.append("" + mAutoCap)
        sb.append("\n   mVibrateOn = ")
        sb.append("" + mVibrateOn)
        sb.append("\n   mSoundOn = ")
        sb.append("" + mSoundOn)
        sb.append("\n   mKeyPreviewPopupOn = ")
        sb.append("" + mKeyPreviewPopupOn)
        sb.append("\n   mShowsVoiceInputKey = ")
        sb.append("" + mShowsVoiceInputKey)
        sb.append("\n   mIncludesOtherImesInLanguageSwitchList = ")
        sb.append("" + mIncludesOtherImesInLanguageSwitchList)
        sb.append("\n   mShowsLanguageSwitchKey = ")
        sb.append("" + mShowsLanguageSwitchKey)
        sb.append("\n   mUseContactsDict = ")
        sb.append("" + mUseContactsDict)
        sb.append("\n   mUsePersonalizedDicts = ")
        sb.append("" + isPersonalizationEnabled)
        sb.append("\n   mUseDoubleSpacePeriod = ")
        sb.append("" + mUseDoubleSpacePeriod)
        sb.append("\n   mBlockPotentiallyOffensive = ")
        sb.append("" + mBlockPotentiallyOffensive)
        sb.append("\n   mBigramPredictionEnabled = ")
        sb.append("" + mBigramPredictionEnabled)
        sb.append("\n   mGestureInputEnabled = ")
        sb.append("" + mGestureInputEnabled)
        sb.append("\n   mGestureTrailEnabled = ")
        sb.append("" + mGestureTrailEnabled)
        sb.append("\n   mGestureFloatingPreviewTextEnabled = ")
        sb.append("" + mGestureFloatingPreviewTextEnabled)
        sb.append("\n   mSlidingKeyInputPreviewEnabled = ")
        sb.append("" + mSlidingKeyInputPreviewEnabled)
        sb.append("\n   mKeyLongpressTimeout = ")
        sb.append("" + mKeyLongpressTimeout)
        sb.append("\n   mLocale = ")
        sb.append("" + mLocale)
        sb.append("\n   mInputAttributes = ")
        sb.append("" + mInputAttributes)
        sb.append("\n   mKeypressVibrationDuration = ")
        sb.append("" + mKeypressVibrationDuration)
        sb.append("\n   mKeypressSoundVolume = ")
        sb.append("" + mKeypressSoundVolume)
        sb.append("\n   mKeyPreviewPopupDismissDelay = ")
        sb.append("" + mKeyPreviewPopupDismissDelay)
        sb.append("\n   mAutoCorrectEnabled = ")
        sb.append("" + mAutoCorrectEnabled)
        sb.append("\n   mAutoCorrectionThreshold = ")
        sb.append("" + mAutoCorrectionThreshold)
        sb.append("\n   mAutoCorrectionEnabledPerUserSettings = ")
        sb.append("" + mAutoCorrectionEnabledPerUserSettings)
        sb.append("\n   mSuggestionsEnabledPerUserSettings = ")
        sb.append("" + isSuggestionsEnabledPerUserSettings)
        sb.append("\n   mDisplayOrientation = ")
        sb.append("" + mDisplayOrientation)
        sb.append("\n   mIsInternal = ")
        sb.append("" + mIsInternal)
        sb.append("\n   mKeyPreviewShowUpDuration = ")
        sb.append("" + mKeyPreviewShowUpDuration)
        sb.append("\n   mKeyPreviewDismissDuration = ")
        sb.append("" + mKeyPreviewDismissDuration)
        sb.append("\n   mKeyPreviewShowUpStartScaleX = ")
        sb.append("" + mKeyPreviewShowUpStartXScale)
        sb.append("\n   mKeyPreviewShowUpStartScaleY = ")
        sb.append("" + mKeyPreviewShowUpStartYScale)
        sb.append("\n   mKeyPreviewDismissEndScaleX = ")
        sb.append("" + mKeyPreviewDismissEndXScale)
        sb.append("\n   mKeyPreviewDismissEndScaleY = ")
        sb.append("" + mKeyPreviewDismissEndYScale)
        return sb.toString()
    }

    companion object {
        private val TAG = SettingsValues::class.java.simpleName
        // "floatMaxValue" and "floatNegativeInfinity" are special marker strings for
        // Float.NEGATIVE_INFINITE and Float.MAX_VALUE. Currently used for auto-correction settings.
        private val FLOAT_MAX_VALUE_MARKER_STRING = "floatMaxValue"
        private val FLOAT_NEGATIVE_INFINITY_MARKER_STRING = "floatNegativeInfinity"
        private val TIMEOUT_TO_GET_TARGET_PACKAGE = 5 // seconds
        const val DEFAULT_SIZE_SCALE = 1.0f // 100%

        private val SUGGESTIONS_VISIBILITY_HIDE_VALUE_OBSOLETE = "2"

        private fun readSuggestionsEnabled(prefs: SharedPreferences): Boolean {
            if (prefs.contains(Settings.PREF_SHOW_SUGGESTIONS_SETTING_OBSOLETE)) {
                val alwaysHide = SUGGESTIONS_VISIBILITY_HIDE_VALUE_OBSOLETE == prefs.getString(Settings.PREF_SHOW_SUGGESTIONS_SETTING_OBSOLETE, null)
                prefs.edit()
                        .remove(Settings.PREF_SHOW_SUGGESTIONS_SETTING_OBSOLETE)
                        .putBoolean(Settings.PREF_SHOW_SUGGESTIONS, !alwaysHide)
                        .apply()
            }
            return prefs.getBoolean(Settings.PREF_SHOW_SUGGESTIONS, true)
        }

        private fun readBigramPredictionEnabled(prefs: SharedPreferences,
                                                res: Resources): Boolean {
            return prefs.getBoolean(Settings.PREF_BIGRAM_PREDICTIONS, res.getBoolean(
                    R.bool.config_default_next_word_prediction))
        }

        private fun readAutoCorrectionThreshold(res: Resources,
                                                currentAutoCorrectionSetting: String): Float {
            val autoCorrectionThresholdValues = res.getStringArray(
                    R.array.auto_correction_threshold_values)
            // When autoCorrectionThreshold is greater than 1.0, it's like auto correction is off.
            val autoCorrectionThreshold: Float
            try {
                val arrayIndex = Integer.parseInt(currentAutoCorrectionSetting)
                if (arrayIndex >= 0 && arrayIndex < autoCorrectionThresholdValues.size) {
                    val `val` = autoCorrectionThresholdValues[arrayIndex]
                    if (FLOAT_MAX_VALUE_MARKER_STRING == `val`) {
                        autoCorrectionThreshold = java.lang.Float.MAX_VALUE
                    } else if (FLOAT_NEGATIVE_INFINITY_MARKER_STRING == `val`) {
                        autoCorrectionThreshold = java.lang.Float.NEGATIVE_INFINITY
                    } else {
                        autoCorrectionThreshold = java.lang.Float.parseFloat(`val`)
                    }
                } else {
                    autoCorrectionThreshold = java.lang.Float.MAX_VALUE
                }
            } catch (e: NumberFormatException) {
                // Whenever the threshold settings are correct, never come here.
                Log.w(TAG, "Cannot load auto correction threshold setting."
                        + " currentAutoCorrectionSetting: " + currentAutoCorrectionSetting
                        + ", autoCorrectionThresholdValues: "
                        + Arrays.toString(autoCorrectionThresholdValues), e)
                return java.lang.Float.MAX_VALUE
            }

            return autoCorrectionThreshold
        }

        private fun needsToShowVoiceInputKey(prefs: SharedPreferences,
                                             res: Resources): Boolean {
            // Migrate preference from {@link Settings#PREF_VOICE_MODE_OBSOLETE} to
            // {@link Settings#PREF_VOICE_INPUT_KEY}.
            if (prefs.contains(Settings.PREF_VOICE_MODE_OBSOLETE)) {
                val voiceModeMain = res.getString(R.string.voice_mode_main)
                val voiceMode = prefs.getString(
                        Settings.PREF_VOICE_MODE_OBSOLETE, voiceModeMain)
                val shouldShowVoiceInputKey = voiceModeMain == voiceMode
                prefs.edit()
                        .putBoolean(Settings.PREF_VOICE_INPUT_KEY, shouldShowVoiceInputKey)
                        // Remove the obsolete preference if exists.
                        .remove(Settings.PREF_VOICE_MODE_OBSOLETE)
                        .apply()
            }
            return prefs.getBoolean(Settings.PREF_VOICE_INPUT_KEY, false)
        }
    }
}
