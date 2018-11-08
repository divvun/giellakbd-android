/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.content.Context
import android.media.AudioManager
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import android.view.View

import com.android.inputmethod.latin.common.Constants
import com.android.inputmethod.latin.settings.SettingsValues

/**
 * This class gathers audio feedback and haptic feedback functions.
 *
 * It offers a consistent and simple interface that allows LatinIME to forget about the
 * complexity of settings and the like.
 */
class AudioAndHapticFeedbackManager private constructor()// Intentional empty constructor for singleton.
{
    private var mAudioManager: AudioManager? = null
    private var mVibrator: Vibrator? = null

    private var mSettingsValues: SettingsValues? = null
    private var mSoundOn: Boolean = false

    private fun initInternal(context: Context) {
        mAudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        mVibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    fun performHapticAndAudioFeedback(code: Int,
                                      viewToPerformHapticFeedbackOn: View) {
        performHapticFeedback(viewToPerformHapticFeedbackOn)
        performAudioFeedback(code)
    }

    fun hasVibrator(): Boolean {
        return mVibrator != null && mVibrator!!.hasVibrator()
    }

    fun vibrate(milliseconds: Long) {
        if (mVibrator == null) {
            return
        }
        mVibrator!!.vibrate(milliseconds)
    }

    private fun reevaluateIfSoundIsOn(): Boolean {
        return if (mSettingsValues == null || !mSettingsValues!!.mSoundOn || mAudioManager == null) {
            false
        } else mAudioManager!!.ringerMode == AudioManager.RINGER_MODE_NORMAL
    }

    fun performAudioFeedback(code: Int) {
        // if mAudioManager is null, we can't play a sound anyway, so return
        if (mAudioManager == null) {
            return
        }
        if (!mSoundOn) {
            return
        }
        val sound: Int
        when (code) {
            Constants.CODE_DELETE -> sound = AudioManager.FX_KEYPRESS_DELETE
            Constants.CODE_ENTER -> sound = AudioManager.FX_KEYPRESS_RETURN
            Constants.CODE_SPACE -> sound = AudioManager.FX_KEYPRESS_SPACEBAR
            else -> sound = AudioManager.FX_KEYPRESS_STANDARD
        }
        mAudioManager!!.playSoundEffect(sound, mSettingsValues!!.mKeypressSoundVolume)
    }

    fun performHapticFeedback(viewToPerformHapticFeedbackOn: View?) {
        if (!mSettingsValues!!.mVibrateOn) {
            return
        }
        if (mSettingsValues!!.mKeypressVibrationDuration >= 0) {
            vibrate(mSettingsValues!!.mKeypressVibrationDuration.toLong())
            return
        }
        // Go ahead with the system default
        viewToPerformHapticFeedbackOn?.performHapticFeedback(
                HapticFeedbackConstants.KEYBOARD_TAP,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
    }

    fun onSettingsChanged(settingsValues: SettingsValues) {
        mSettingsValues = settingsValues
        mSoundOn = reevaluateIfSoundIsOn()
    }

    fun onRingerModeChanged() {
        mSoundOn = reevaluateIfSoundIsOn()
    }

    companion object {
        @JvmStatic
        val instance = AudioAndHapticFeedbackManager()

        fun init(context: Context) {
            instance.initInternal(context)
        }
    }
}
