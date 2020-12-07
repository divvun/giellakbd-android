/*
 * Copyright (C) 2014 The Android Open Source Project
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

import android.os.Build
import android.os.Bundle
import com.android.inputmethod.latin.BuildConfig
import com.android.inputmethod.latin.R

/**
 * "Accounts & Privacy" settings sub screen.
 *
 * This settings sub screen handles the following preferences:
 *  *  Account selection/management for IME
 *  *  Sync preferences
 *  *  Privacy preferences
 */
class AboutSettingsFragment : SubScreenFragment() {
    override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)
        addPreferencesFromResource(R.xml.prefs_screen_about)
    }

    override fun onResume() {
        super.onResume()
        val versionName = findPreference("version_name")
        versionName.summary = BuildConfig.VERSION_NAME
        val versionCode = findPreference("version_code")
        versionCode.summary = "${BuildConfig.VERSION_CODE}"
    }
}
