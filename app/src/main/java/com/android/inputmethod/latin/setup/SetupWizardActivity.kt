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

package com.android.inputmethod.latin.setup

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Message
import android.provider.Settings
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.work.WorkInfo

import com.android.inputmethod.compat.TextViewCompatUtils
import com.android.inputmethod.compat.ViewCompatUtils
import com.android.inputmethod.latin.R
import com.android.inputmethod.latin.settings.SettingsActivity
import com.android.inputmethod.latin.utils.LeakGuardHandlerWrapper
import com.android.inputmethod.latin.utils.UncachedInputMethodManagerUtils
import no.divvun.pahkat.WORKMANAGER_TAG_UPDATE
import no.divvun.pahkat.workManager
import timber.log.Timber

import java.util.ArrayList

// TODO: Use Fragment to implement welcome screen and setup steps.
class SetupWizardActivity : Activity(), View.OnClickListener {

    private lateinit var mImm: InputMethodManager

    private lateinit var mSetupWizard: View
    private lateinit var mWelcomeScreen: View
    private lateinit var mSetupScreen: View
    private lateinit var mActionStart: View
    private lateinit var mActionNext: View
    private lateinit var mStep1Bullet: TextView
    private lateinit var mActionFinish: TextView
    private lateinit var mSetupStepGroup: SetupStepGroup
    private var mStepNumber: Int = 0
    private var mNeedsToAdjustStepNumberToSystemState: Boolean = false
    private var hasSeenSubtypes = false

    private var mHandler: SettingsPoolingHandler? = null

    private class SettingsPoolingHandler(ownerInstance: SetupWizardActivity,
                                         private val mImmInHandler: InputMethodManager) : LeakGuardHandlerWrapper<SetupWizardActivity>(ownerInstance) {

        override fun handleMessage(msg: Message) {
            val setupWizardActivity = ownerInstance ?: return
            when (msg.what) {
                MSG_POLLING_IME_SETTINGS -> {
                    if (UncachedInputMethodManagerUtils.isThisImeEnabled(setupWizardActivity,
                                    mImmInHandler)) {
                        setupWizardActivity.invokeSetupWizardOfThisIme()
                        return
                    }
                    startPollingImeSettings()
                }
            }
        }

        fun startPollingImeSettings() {
            sendMessageDelayed(obtainMessage(MSG_POLLING_IME_SETTINGS),
                    IME_SETTINGS_POLLING_INTERVAL)
        }

        fun cancelPollingImeSettings() {
            removeMessages(MSG_POLLING_IME_SETTINGS)
        }

        companion object {
            private const val MSG_POLLING_IME_SETTINGS = 0
            private const val IME_SETTINGS_POLLING_INTERVAL: Long = 200
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(android.R.style.Theme_Translucent_NoTitleBar)
        super.onCreate(savedInstanceState)

        mImm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        mHandler = SettingsPoolingHandler(this, mImm)

        setContentView(R.layout.setup_wizard)
        mSetupWizard = findViewById(R.id.setup_wizard)
        
        // Handle edge-to-edge display for Android 15+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            handleEdgeToEdge()
        }

        mStepNumber = savedInstanceState?.getInt(STATE_STEP) ?: determineSetupStepNumberFromLauncher()

        val applicationName = resources.getString(applicationInfo.labelRes)
        mWelcomeScreen = findViewById(R.id.setup_welcome_screen)
        val welcomeTitle = findViewById<View>(R.id.setup_welcome_title) as TextView
        welcomeTitle.text = getString(R.string.setup_welcome_title, applicationName)

        mSetupScreen = findViewById(R.id.setup_steps_screen)
        val stepsTitle = findViewById<View>(R.id.setup_title) as TextView
        stepsTitle.text = getString(R.string.setup_steps_title, applicationName)

        val indicatorView = findViewById<View>(R.id.setup_step_indicator) as SetupStepIndicatorView
        mSetupStepGroup = SetupStepGroup(indicatorView)

        mStep1Bullet = findViewById<View>(R.id.setup_step1_bullet) as TextView
        mStep1Bullet.setOnClickListener(this)
        val step1 = SetupStep(STEP_1, applicationName,
                mStep1Bullet, findViewById(R.id.setup_step1),
                R.string.setup_step1_title, R.string.setup_step1_instruction,
                R.string.setup_step1_finished_instruction, R.drawable.ic_setup_step1,
                R.string.setup_step1_action)
        val handler = mHandler
        step1.setAction(Runnable {
            invokeLanguageAndInputSettings()
            handler!!.startPollingImeSettings()
        })
        mSetupStepGroup.addStep(step1)

        val step2 = SetupStep(STEP_2, applicationName,
                findViewById<View>(R.id.setup_step2_bullet) as TextView, findViewById(R.id.setup_step2),
                R.string.setup_step2_title, R.string.setup_step2_instruction,
                0 /* finishedInstruction */, R.drawable.ic_setup_step2,
                R.string.setup_step2_action)
        step2.setAction(Runnable { invokeInputMethodPicker() })
        mSetupStepGroup.addStep(step2)

        val step3 = SetupStep(STEP_3, applicationName,
                findViewById<View>(R.id.setup_step3_bullet) as TextView, findViewById(R.id.setup_step3),
                R.string.setup_step3_title, R.string.setup_step3_instruction,
                0 /* finishedInstruction */, R.drawable.ic_setup_step3,
                R.string.setup_step3_action)
        step3.setAction(Runnable { invokeSubtypeEnablerOfThisIme() })
        mSetupStepGroup.addStep(step3)

        mActionStart = findViewById(R.id.setup_start_label)
        mActionStart.setOnClickListener(this)
        mActionNext = findViewById(R.id.setup_next)
        mActionNext.setOnClickListener(this)
        mActionFinish = findViewById<View>(R.id.setup_finish) as TextView
        TextViewCompatUtils.setCompoundDrawablesRelativeWithIntrinsicBounds(mActionFinish,
                resources.getDrawable(R.drawable.ic_setup_finish), null, null, null)
        mActionFinish.setOnClickListener(this)
    }

    private fun handleEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(mSetupWizard) { view, insets ->
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            
            val originalPaddingLeft = resources.getDimensionPixelSize(R.dimen.setup_horizontal_padding)
            val originalPaddingTop = resources.getDimensionPixelSize(R.dimen.setup_vertical_padding)
            val originalPaddingRight = resources.getDimensionPixelSize(R.dimen.setup_horizontal_padding)
            val originalPaddingBottom = resources.getDimensionPixelSize(R.dimen.setup_vertical_padding)
            
            view.setPadding(
                originalPaddingLeft + systemBars.left,
                originalPaddingTop + systemBars.top,
                originalPaddingRight + systemBars.right,
                originalPaddingBottom + systemBars.bottom
            )
            
            insets
        }
    }

    override fun onClick(v: View) {
        if (v === mActionFinish) {
            finish()
            return
        }
        val currentStep = determineSetupStepNumber()
        val nextStep: Int
        nextStep = if (v === mActionStart) {
            STEP_1
        } else if (v === mActionNext) {
            mStepNumber + 1
        } else if (v === mStep1Bullet && currentStep == STEP_2) {
            STEP_1
        } else {
            mStepNumber
        }
        if (mStepNumber != nextStep) {
            mStepNumber = nextStep
            updateSetupStepView()
        }
    }

    internal fun invokeSetupWizardOfThisIme() {
        val intent = Intent()
        intent.setClass(this, SetupWizardActivity::class.java)
        intent.flags = (Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                or Intent.FLAG_ACTIVITY_SINGLE_TOP
                or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
        mNeedsToAdjustStepNumberToSystemState = true
    }

    private fun invokeSettingsOfThisIme() {
        val intent = Intent()
        intent.setClass(this, SettingsActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or Intent.FLAG_ACTIVITY_CLEAR_TOP
        intent.putExtra(SettingsActivity.EXTRA_ENTRY_KEY,
                SettingsActivity.EXTRA_ENTRY_VALUE_APP_ICON)
        startActivity(intent)
    }

    private fun invokeLanguageAndInputSettings() {
        val intent = Intent()
        intent.action = Settings.ACTION_INPUT_METHOD_SETTINGS
        intent.addCategory(Intent.CATEGORY_DEFAULT)
        startActivity(intent)
        mNeedsToAdjustStepNumberToSystemState = true
    }

    private fun invokeInputMethodPicker() {
        // Invoke input method picker.
        mImm.showInputMethodPicker()
        mNeedsToAdjustStepNumberToSystemState = true
    }

    private fun invokeSubtypeEnablerOfThisIme() {
        hasSeenSubtypes = true
        val imi = UncachedInputMethodManagerUtils.getInputMethodInfoOf(packageName, mImm)
                ?: return
        val intent = Intent()
        intent.action = Settings.ACTION_INPUT_METHOD_SUBTYPE_SETTINGS
        intent.addCategory(Intent.CATEGORY_DEFAULT)
        intent.putExtra(Settings.EXTRA_INPUT_METHOD_ID, imi.id)
        startActivity(intent)
    }

    private fun determineSetupStepNumberFromLauncher(): Int {
        val stepNumber = determineSetupStepNumber()
        if (stepNumber == STEP_1) {
            return STEP_WELCOME
        }
        return if (stepNumber == STEP_3) {
            STEP_LAUNCHING_IME_SETTINGS
        } else stepNumber
    }

    private fun determineSetupStepNumber(): Int {
        mHandler!!.cancelPollingImeSettings()
        if (!UncachedInputMethodManagerUtils.isThisImeEnabled(this, mImm)) {
            return STEP_1
        }
        return if (!UncachedInputMethodManagerUtils.isThisImeCurrent(this, mImm)) {
            STEP_2
        } else STEP_3
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_STEP, mStepNumber)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        mStepNumber = savedInstanceState.getInt(STATE_STEP)
    }

    override fun onRestart() {
        super.onRestart()
        // Probably the setup wizard has been invoked from "Recent" menu. The setup step number
        // needs to be adjusted to system state, because the state (IME is enabled and/or current)
        // may have been changed.
        if (isInSetupSteps(mStepNumber)) {
            mStepNumber = determineSetupStepNumber()
        }
    }

    override fun onResume() {
        super.onResume()
        if (mStepNumber == STEP_LAUNCHING_IME_SETTINGS) {
            // Prevent white screen flashing while launching settings activity.
            mSetupWizard.visibility = View.INVISIBLE
            invokeSettingsOfThisIme()
            mStepNumber = STEP_BACK_FROM_IME_SETTINGS
            return
        }
        if (mStepNumber == STEP_BACK_FROM_IME_SETTINGS) {
            finish()
            return
        }
        updateSetupStepView()
        observeDownload()
    }

    private fun observeDownload() {
        // Get the LiveData for the work with a specific tag
        this.workManager().getWorkInfosByTagLiveData(WORKMANAGER_TAG_UPDATE)
            .observeForever { workInfos ->
                val workInfo = workInfos.firstOrNull() ?: return@observeForever
                Timber.d("WorkInfo $workInfo")

                if(mActionFinish.visibility == View.VISIBLE) {
                    if (workInfo.state == WorkInfo.State.RUNNING) {
                        mActionFinish.text = getString(R.string.dictionary_downloading)
                        TextViewCompatUtils.setCompoundDrawablesRelativeWithIntrinsicBounds(
                            mActionFinish,
                            resources.getDrawable(R.drawable.ic_setup_download), null, null, null
                        )
                    } else {
                        mActionFinish.text = getString(R.string.setup_finish_action)
                        TextViewCompatUtils.setCompoundDrawablesRelativeWithIntrinsicBounds(
                            mActionFinish,
                            resources.getDrawable(R.drawable.ic_setup_finish), null, null, null
                        )
                    }
                }
            }
    }

    override fun onBackPressed() {
        if (mStepNumber == STEP_1) {
            mStepNumber = STEP_WELCOME
            updateSetupStepView()
            return
        }
        super.onBackPressed()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && mNeedsToAdjustStepNumberToSystemState) {
            mNeedsToAdjustStepNumberToSystemState = false
            mStepNumber = determineSetupStepNumber()
            updateSetupStepView()
        }
    }

    private fun isFinishVisible(): Boolean {
        return mStepNumber == STEP_3 && hasSeenSubtypes
    }

    private fun updateSetupStepView() {
        mSetupWizard.visibility = View.VISIBLE
        val welcomeScreen = mStepNumber == STEP_WELCOME
        mWelcomeScreen.visibility = if (welcomeScreen) View.VISIBLE else View.GONE
        mSetupScreen.visibility = if (welcomeScreen) View.GONE else View.VISIBLE

        val isStepActionAlreadyDone = mStepNumber < determineSetupStepNumber()
        mSetupStepGroup.enableStep(mStepNumber, isStepActionAlreadyDone)
        mActionNext.visibility = if (isStepActionAlreadyDone) View.VISIBLE else View.GONE
        mActionFinish.visibility = if (isFinishVisible()) View.VISIBLE else View.GONE
    }

    internal class SetupStep(val mStepNo: Int, applicationName: String, private val mBulletView: TextView,
                             private val mStepView: View, title: Int, instruction: Int,
                             finishedInstruction: Int, actionIcon: Int, actionLabel: Int) : View.OnClickListener {
        private val mActivatedColor: Int
        private val mDeactivatedColor: Int
        private val mInstruction: String?
        private val mFinishedInstruction: String?
        private val mActionLabel: TextView
        private var mAction: Runnable? = null

        init {
            val res = mStepView.resources
            mActivatedColor = res.getColor(R.color.setup_text_action)
            mDeactivatedColor = res.getColor(R.color.setup_text_dark)

            val titleView = mStepView.findViewById<View>(R.id.setup_step_title) as TextView
            titleView.text = res.getString(title, applicationName)
            mInstruction = if (instruction == 0)
                null
            else
                res.getString(instruction, applicationName)
            mFinishedInstruction = if (finishedInstruction == 0)
                null
            else
                res.getString(finishedInstruction, applicationName)

            mActionLabel = mStepView.findViewById<View>(R.id.setup_step_action_label) as TextView
            mActionLabel.text = res.getString(actionLabel)
            if (actionIcon == 0) {
                val paddingEnd = ViewCompatUtils.getPaddingEnd(mActionLabel)
                ViewCompatUtils.setPaddingRelative(mActionLabel, paddingEnd, 0, paddingEnd, 0)
            } else {
                TextViewCompatUtils.setCompoundDrawablesRelativeWithIntrinsicBounds(
                        mActionLabel, res.getDrawable(actionIcon), null, null, null)
            }
        }

        fun setEnabled(enabled: Boolean, isStepActionAlreadyDone: Boolean) {
            mStepView.visibility = if (enabled) View.VISIBLE else View.GONE
            mBulletView.setTextColor(if (enabled) mActivatedColor else mDeactivatedColor)
            val instructionView = mStepView.findViewById<View>(
                    R.id.setup_step_instruction) as TextView
            instructionView.text = if (isStepActionAlreadyDone) mFinishedInstruction else mInstruction
            mActionLabel.visibility = if (isStepActionAlreadyDone) View.GONE else View.VISIBLE
        }

        fun setAction(action: Runnable) {
            mActionLabel.setOnClickListener(this)
            mAction = action
        }

        override fun onClick(v: View) {
            if (v === mActionLabel && mAction != null) {
                mAction!!.run()
                return
            }
        }
    }

    internal class SetupStepGroup(private val mIndicatorView: SetupStepIndicatorView) {
        private val mGroup = ArrayList<SetupStep>()

        fun addStep(step: SetupStep) {
            mGroup.add(step)
        }

        fun enableStep(enableStepNo: Int, isStepActionAlreadyDone: Boolean) {
            for (step in mGroup) {
                step.setEnabled(step.mStepNo == enableStepNo, isStepActionAlreadyDone)
            }
            mIndicatorView.setIndicatorPosition(enableStepNo - STEP_1, mGroup.size)
        }
    }

    companion object {
        internal val TAG = SetupWizardActivity::class.java.simpleName

        private const val STATE_STEP = "step"
        private const val STEP_WELCOME = 0
        private const val STEP_1 = 1
        private const val STEP_2 = 2
        private const val STEP_3 = 3
        private const val STEP_LAUNCHING_IME_SETTINGS = 4
        private const val STEP_BACK_FROM_IME_SETTINGS = 5

        private fun isInSetupSteps(stepNumber: Int): Boolean {
            return stepNumber in STEP_1..STEP_3
        }
    }
}
