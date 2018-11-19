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

import android.content.Intent
import android.os.Bundle
import android.os.Message
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.core.content.getSystemService
import androidx.fragment.app.Fragment

import com.android.inputmethod.latin.R
import com.android.inputmethod.latin.settings.SettingsActivity
import com.android.inputmethod.latin.utils.LeakGuardHandlerWrapper
import com.android.inputmethod.latin.utils.UncachedInputMethodManagerUtils
import kotlinx.android.synthetic.main.fragment_setup_wizard.view.*

// TODO: Use Fragment to implement welcome screen and setup steps.
class SetupWizardFragment : Fragment() {
    private lateinit var imm: InputMethodManager
    private lateinit var handler: SettingsPollingHandler

    private var imeSettingTriggered = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_setup_wizard, container, false)

        imm = activity!!.getSystemService<InputMethodManager>()!!
        handler = SettingsPollingHandler(this, imm)

        val handler = handler
        view.step1.setOnClickListener {
            invokeLanguageAndInputSettings()
            handler.startPollingImeSettings()
        }

        view.step2.setOnClickListener {
            invokeInputMethodPicker()
        }

        /**
        view.step3.setOnClickListener {
            invokeSubtypeEnablerOfThisIme()
        }*/

        // Listener for changes on WindowFocusChanged (return from change of InputMethod
        view.viewTreeObserver.addOnWindowFocusChangeListener{
            // Window has focus
            if(it) {
                val step = determineSetupStep()
                updateSetupStep(step)
            }
        }

        return view
    }

    internal fun invokeSetupWizardOfThisIme() {
        val intent = Intent()
        intent.setClass(activity!!, SetupWizardFragment::class.java)
        intent.flags = (Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                or Intent.FLAG_ACTIVITY_SINGLE_TOP
                or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
    }

    private fun invokeSettingsOfThisIme() {
        val intent = Intent()
        intent.setClass(activity!!, SettingsActivity::class.java)
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
    }

    private fun invokeInputMethodPicker() {
        // Invoke input method picker.
        imm.showInputMethodPicker()
    }

    // TODO(rawa) Is this needed? Will subType be enabled?
    private fun invokeSubtypeEnablerOfThisIme() {
        val imi = UncachedInputMethodManagerUtils.getInputMethodInfoOf(activity!!.packageName, imm)
                ?: return
        val intent = Intent()
        intent.action = Settings.ACTION_INPUT_METHOD_SUBTYPE_SETTINGS
        intent.addCategory(Intent.CATEGORY_DEFAULT)
        intent.putExtra(Settings.EXTRA_INPUT_METHOD_ID, imi.id)
        startActivity(intent)
    }

    private fun determineSetupStep(): SetupStep {
        handler.cancelPollingImeSettings()
        if (!UncachedInputMethodManagerUtils.isThisImeEnabled(activity!!, imm)) {
            return SetupStep.StepIME
        }
        return if (!UncachedInputMethodManagerUtils.isThisImeCurrent(activity!!, imm)) {
            SetupStep.StepSelectInput
        } else SetupStep.StepComplete
    }

    override fun onResume() {
        super.onResume()
        val step = determineSetupStep()
        if(step is SetupStep.StepComplete){
            invokeSettingsOfThisIme()
            if(imeSettingTriggered){
                activity!!.finish()
                return
            } else {
                imeSettingTriggered = true
            }
        }
        updateSetupStep(step)
    }

    private fun updateSetupStep(step: SetupStep) {

        when(step){
            SetupStep.StepIME -> {
                view!!.step1.isEnabled = true
                view!!.step2.isEnabled = true
            }
            SetupStep.StepSelectInput -> {
                view!!.step1.isEnabled = false
                view!!.step2.isEnabled = true
            }
            SetupStep.StepComplete -> {
                view!!.step1.isEnabled = false
                view!!.step2.isEnabled = false
            }
        }
        Log.d(tag, "stepNumber: ${determineSetupStep()}")
    }

    sealed class SetupStep {
        object StepIME: SetupStep()
        object StepSelectInput: SetupStep()
        object StepSubType: SetupStep()
        object StepComplete: SetupStep()
    }

    private class SettingsPollingHandler(ownerInstance: SetupWizardFragment,
                                         private val immInHandler: InputMethodManager) : LeakGuardHandlerWrapper<SetupWizardFragment>(ownerInstance) {

        override fun handleMessage(msg: Message) {
            val setupWizardFragment = ownerInstance ?: return
            when (msg.what) {
                MSG_POLLING_IME_SETTINGS -> {
                    if (UncachedInputMethodManagerUtils.isThisImeEnabled(setupWizardFragment.activity,
                                    immInHandler)) {
                        setupWizardFragment.invokeSetupWizardOfThisIme()
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

}
