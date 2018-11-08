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

package com.android.inputmethod.keyboard

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Build.VERSION_CODES
import android.preference.PreferenceManager
import android.util.Log

import com.android.inputmethod.latin.R

import java.util.ArrayList
import java.util.Arrays

class KeyboardTheme// Note: The themeId should be aligned with "themeId" attribute of Keyboard style
// in values/themes-<style>.xml.
internal constructor(val mThemeId: Int, val mThemeName: String, val mStyleId: Int,
                    val mMinApiVersion: Int) : Comparable<KeyboardTheme> {

    override fun compareTo(rhs: KeyboardTheme): Int {
        if (mMinApiVersion > rhs.mMinApiVersion) return -1
        return if (mMinApiVersion < rhs.mMinApiVersion) 1 else 0
    }

    override fun equals(o: Any?): Boolean {
        return if (o === this) true else o is KeyboardTheme && o.mThemeId == mThemeId
    }

    override fun hashCode(): Int {
        return mThemeId
    }

    companion object {
        private val TAG = KeyboardTheme::class.java.simpleName

        internal val KLP_KEYBOARD_THEME_KEY = "pref_keyboard_layout_20110916"
        internal val LXX_KEYBOARD_THEME_KEY = "pref_keyboard_theme_20140509"

        // These should be aligned with Keyboard.themeId and Keyboard.Case.keyboardTheme
        // attributes' values in attrs.xml.
        val THEME_ID_ICS = 0
        val THEME_ID_KLP = 2
        val THEME_ID_LXX_LIGHT = 3
        val THEME_ID_LXX_DARK = 4
        val DEFAULT_THEME_ID = THEME_ID_KLP

        private var AVAILABLE_KEYBOARD_THEMES: Array<KeyboardTheme> = emptyArray()

        /* package private for testing */
        internal val KEYBOARD_THEMES = arrayOf(KeyboardTheme(THEME_ID_ICS, "ICS", R.style.KeyboardTheme_ICS,
                // This has never been selected because we support ICS or later.
                VERSION_CODES.BASE), KeyboardTheme(THEME_ID_KLP, "KLP", R.style.KeyboardTheme_KLP,
                // Default theme for ICS, JB, and KLP.
                VERSION_CODES.ICE_CREAM_SANDWICH), KeyboardTheme(THEME_ID_LXX_LIGHT, "LXXLight", R.style.KeyboardTheme_LXX_Light,
                // Default theme for LXX.
                Build.VERSION_CODES.LOLLIPOP), KeyboardTheme(THEME_ID_LXX_DARK, "LXXDark", R.style.KeyboardTheme_LXX_Dark,
                // This has never been selected as default theme.
                VERSION_CODES.BASE))

        init {
            // Sort {@link #KEYBOARD_THEME} by descending order of {@link #mMinApiVersion}.
            Arrays.sort(KEYBOARD_THEMES)
        }

        /* package private for testing */
        internal fun searchKeyboardThemeById(themeId: Int,
                                             availableThemeIds: Array<KeyboardTheme>): KeyboardTheme? {
            // TODO: This search algorithm isn't optimal if there are many themes.
            for (theme in availableThemeIds) {
                if (theme.mThemeId == themeId) {
                    return theme
                }
            }
            return null
        }

        /* package private for testing */
        internal fun getDefaultKeyboardTheme(prefs: SharedPreferences,
                                             sdkVersion: Int, availableThemeArray: Array<KeyboardTheme>): KeyboardTheme? {
            val klpThemeIdString = prefs.getString(KLP_KEYBOARD_THEME_KEY, null)
            if (klpThemeIdString != null) {
                if (sdkVersion <= VERSION_CODES.KITKAT) {
                    try {
                        val themeId = Integer.parseInt(klpThemeIdString)
                        val theme = searchKeyboardThemeById(themeId,
                                availableThemeArray)
                        if (theme != null) {
                            return theme
                        }
                        Log.w(TAG, "Unknown keyboard theme in KLP preference: $klpThemeIdString")
                    } catch (e: NumberFormatException) {
                        Log.w(TAG, "Illegal keyboard theme in KLP preference: $klpThemeIdString", e)
                    }

                }
                // Remove old preference.
                Log.i(TAG, "Remove KLP keyboard theme preference: $klpThemeIdString")
                prefs.edit().remove(KLP_KEYBOARD_THEME_KEY).apply()
            }
            // TODO: This search algorithm isn't optimal if there are many themes.
            for (theme in availableThemeArray) {
                if (sdkVersion >= theme.mMinApiVersion) {
                    return theme
                }
            }
            return searchKeyboardThemeById(DEFAULT_THEME_ID, availableThemeArray)
        }

        fun getKeyboardThemeName(themeId: Int): String {
            val theme = searchKeyboardThemeById(themeId, KEYBOARD_THEMES)
            return theme!!.mThemeName
        }

        fun saveKeyboardThemeId(themeId: Int, prefs: SharedPreferences) {
            saveKeyboardThemeId(themeId, prefs, Build.VERSION.SDK_INT)
        }

        /* package private for testing */
        internal fun getPreferenceKey(sdkVersion: Int): String {
            return if (sdkVersion <= VERSION_CODES.KITKAT) {
                KLP_KEYBOARD_THEME_KEY
            } else LXX_KEYBOARD_THEME_KEY
        }

        /* package private for testing */
        internal fun saveKeyboardThemeId(themeId: Int, prefs: SharedPreferences,
                                         sdkVersion: Int) {
            val prefKey = getPreferenceKey(sdkVersion)
            prefs.edit().putString(prefKey, Integer.toString(themeId)).apply()
        }

        fun getKeyboardTheme(context: Context): KeyboardTheme? {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val availableThemeArray = getAvailableThemeArray(context)
            return getKeyboardTheme(prefs, Build.VERSION.SDK_INT, availableThemeArray)
        }

        /* package private for testing */
        internal fun getAvailableThemeArray(context: Context): Array<KeyboardTheme> {
            if (AVAILABLE_KEYBOARD_THEMES.isEmpty()) {
                val availableThemeIdStringArray = context.resources.getIntArray(
                        R.array.keyboard_theme_ids)
                val availableThemeList = ArrayList<KeyboardTheme>()
                for (id in availableThemeIdStringArray) {
                    val theme = searchKeyboardThemeById(id, KEYBOARD_THEMES)
                    if (theme != null) {
                        availableThemeList.add(theme)
                    }
                }
                AVAILABLE_KEYBOARD_THEMES = availableThemeList.toTypedArray()
                Arrays.sort(AVAILABLE_KEYBOARD_THEMES)
            }
            return AVAILABLE_KEYBOARD_THEMES
        }

        /* package private for testing */
        internal fun getKeyboardTheme(prefs: SharedPreferences, sdkVersion: Int,
                                      availableThemeArray: Array<KeyboardTheme>): KeyboardTheme? {
            val lxxThemeIdString = prefs.getString(LXX_KEYBOARD_THEME_KEY, null)
                    ?: return getDefaultKeyboardTheme(prefs, sdkVersion, availableThemeArray)
            try {
                val themeId = Integer.parseInt(lxxThemeIdString)
                val theme = searchKeyboardThemeById(themeId, availableThemeArray)
                if (theme != null) {
                    return theme
                }
                Log.w(TAG, "Unknown keyboard theme in LXX preference: $lxxThemeIdString")
            } catch (e: NumberFormatException) {
                Log.w(TAG, "Illegal keyboard theme in LXX preference: $lxxThemeIdString", e)
            }

            // Remove preference that contains unknown or illegal theme id.
            prefs.edit().remove(LXX_KEYBOARD_THEME_KEY).apply()
            return getDefaultKeyboardTheme(prefs, sdkVersion, availableThemeArray)
        }
    }
}
