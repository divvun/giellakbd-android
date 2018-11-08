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

package com.android.inputmethod.latin

import com.android.inputmethod.latin.common.Constants.Subtype.KEYBOARD_MODE

import android.os.Build
import android.util.Log
import android.view.inputmethod.InputMethodSubtype

import com.android.inputmethod.compat.InputMethodSubtypeCompatUtils
import com.android.inputmethod.latin.common.Constants
import com.android.inputmethod.latin.common.LocaleUtils
import com.android.inputmethod.latin.utils.SubtypeLocaleUtils

import java.util.HashMap
import java.util.Locale

/**
 * Enrichment class for InputMethodSubtype to enable concurrent multi-lingual input.
 *
 * Right now, this returns the extra value of its primary subtype.
 */
// non final for easy mocking.
class RichInputMethodSubtype(// TODO: remove this method
        val rawSubtype: InputMethodSubtype) {
    val locale: Locale
    val originalLocale: Locale

    // The mode is also determined by the primary subtype.
    val mode: String
        get() = rawSubtype.mode

    val isNoLanguage: Boolean
        get() = SubtypeLocaleUtils.NO_LANGUAGE == rawSubtype.locale

    val nameForLogging: String
        get() = toString()

    // InputMethodSubtype's display name for spacebar text in its locale.
    //        isAdditionalSubtype (T=true, F=false)
    // locale layout  |  Middle      Full
    // ------ ------- - --------- ----------------------
    //  en_US qwerty  F  English   English (US)           exception
    //  en_GB qwerty  F  English   English (UK)           exception
    //  es_US spanish F  Español   Español (EE.UU.)       exception
    //  fr    azerty  F  Français  Français
    //  fr_CA qwerty  F  Français  Français (Canada)
    //  fr_CH swiss   F  Français  Français (Suisse)
    //  de    qwertz  F  Deutsch   Deutsch
    //  de_CH swiss   T  Deutsch   Deutsch (Schweiz)
    //  zz    qwerty  F  QWERTY    QWERTY
    //  fr    qwertz  T  Français  Français
    //  de    qwerty  T  Deutsch   Deutsch
    //  en_US azerty  T  English   English (US)
    //  zz    azerty  T  AZERTY    AZERTY
    // Get the RichInputMethodSubtype's full display name in its locale.
    val fullDisplayName: String
        get() = if (isNoLanguage) {
            SubtypeLocaleUtils.getKeyboardLayoutSetDisplayName(rawSubtype)
        } else SubtypeLocaleUtils.getSubtypeLocaleDisplayName(rawSubtype.locale)

    // Get the RichInputMethodSubtype's middle display name in its locale.
    val middleDisplayName: String
        get() = if (isNoLanguage) {
            SubtypeLocaleUtils.getKeyboardLayoutSetDisplayName(rawSubtype)
        } else SubtypeLocaleUtils.getSubtypeLanguageDisplayName(rawSubtype.locale)

    // The subtype is considered RTL if the language of the main subtype is RTL.
    val isRtlSubtype: Boolean
        get() = LocaleUtils.isRtlLanguage(locale)

    val keyboardLayoutSetName: String
        get() = SubtypeLocaleUtils.getKeyboardLayoutSetName(rawSubtype)

    init {
        originalLocale = InputMethodSubtypeCompatUtils.getLocaleObject(rawSubtype)
        val mappedLocale = sLocaleMap[originalLocale]
        locale = mappedLocale ?: originalLocale
    }

    // Extra values are determined by the primary subtype. This is probably right, but
    // we may have to revisit this later.
    fun getExtraValueOf(key: String): String {
        return rawSubtype.getExtraValueOf(key)
    }

    override fun equals(other: Any?): Boolean {
        if (other !is RichInputMethodSubtype) {
            return false
        }
        val o = other as RichInputMethodSubtype?
        return rawSubtype == o?.rawSubtype && locale == o.locale
    }

    override fun hashCode(): Int {
        return rawSubtype.hashCode() + locale.hashCode()
    }

    override fun toString(): String {
        return "Multi-lingual subtype: $rawSubtype, $locale"
    }

    companion object {
        private val TAG = RichInputMethodSubtype::class.java.simpleName

        private val sLocaleMap = initializeLocaleMap()
        private fun initializeLocaleMap(): HashMap<Locale, Locale> {
            val map = HashMap<Locale, Locale>()
            // TODO: Remove this workaround once when we become able to deal with "sr-Latn".
            map[Locale.forLanguageTag("sr-Latn")] = Locale("sr_ZZ")
            return map
        }

        fun getRichInputMethodSubtype(
                subtype: InputMethodSubtype?): RichInputMethodSubtype {
            return if (subtype == null) {
                noLanguageSubtype
            } else {
                RichInputMethodSubtype(subtype)
            }
        }

        // Dummy no language QWERTY subtype. See {@link R.xml.method}.
        private val SUBTYPE_ID_OF_DUMMY_NO_LANGUAGE_SUBTYPE = -0x221f402d
        private val EXTRA_VALUE_OF_DUMMY_NO_LANGUAGE_SUBTYPE = (
                "KeyboardLayoutSet=" + SubtypeLocaleUtils.QWERTY
                        + "," + Constants.Subtype.ExtraValue.ASCII_CAPABLE
                        + "," + Constants.Subtype.ExtraValue.ENABLED_WHEN_DEFAULT_IS_NOT_ASCII_CAPABLE
                        + "," + Constants.Subtype.ExtraValue.EMOJI_CAPABLE)
        private val DUMMY_NO_LANGUAGE_SUBTYPE = RichInputMethodSubtype(InputMethodSubtypeCompatUtils.newInputMethodSubtype(
                R.string.subtype_no_language_qwerty, R.drawable.ic_ime_switcher_dark,
                SubtypeLocaleUtils.NO_LANGUAGE, KEYBOARD_MODE,
                EXTRA_VALUE_OF_DUMMY_NO_LANGUAGE_SUBTYPE,
                false /* isAuxiliary */, false /* overridesImplicitlyEnabledSubtype */,
                SUBTYPE_ID_OF_DUMMY_NO_LANGUAGE_SUBTYPE))
        // Caveat: We probably should remove this when we add an Emoji subtype in {@link R.xml.method}.
        // Dummy Emoji subtype. See {@link R.xml.method}.
        private val SUBTYPE_ID_OF_DUMMY_EMOJI_SUBTYPE = -0x2874d130
        private val EXTRA_VALUE_OF_DUMMY_EMOJI_SUBTYPE = (
                "KeyboardLayoutSet=" + SubtypeLocaleUtils.EMOJI
                        + "," + Constants.Subtype.ExtraValue.EMOJI_CAPABLE)
        private val DUMMY_EMOJI_SUBTYPE = RichInputMethodSubtype(
                InputMethodSubtypeCompatUtils.newInputMethodSubtype(
                        R.string.subtype_emoji, R.drawable.ic_ime_switcher_dark,
                        SubtypeLocaleUtils.NO_LANGUAGE, KEYBOARD_MODE,
                        EXTRA_VALUE_OF_DUMMY_EMOJI_SUBTYPE,
                        false /* isAuxiliary */, false /* overridesImplicitlyEnabledSubtype */,
                        SUBTYPE_ID_OF_DUMMY_EMOJI_SUBTYPE))
        private var sNoLanguageSubtype: RichInputMethodSubtype? = null
        private var sEmojiSubtype: RichInputMethodSubtype? = null

        val noLanguageSubtype: RichInputMethodSubtype
            get() {
                var noLanguageSubtype = sNoLanguageSubtype
                if (noLanguageSubtype == null) {
                    val rawNoLanguageSubtype = RichInputMethodManager.getInstance()
                            .findSubtypeByLocaleAndKeyboardLayoutSet(
                                    SubtypeLocaleUtils.NO_LANGUAGE, SubtypeLocaleUtils.QWERTY)
                    if (rawNoLanguageSubtype != null) {
                        noLanguageSubtype = RichInputMethodSubtype(rawNoLanguageSubtype)
                    }
                }
                if (noLanguageSubtype != null) {
                    sNoLanguageSubtype = noLanguageSubtype
                    return noLanguageSubtype
                }
                Log.w(TAG, "Can't find any language with QWERTY subtype")
                Log.w(TAG, "No input method subtype found; returning dummy subtype: $DUMMY_NO_LANGUAGE_SUBTYPE")
                return DUMMY_NO_LANGUAGE_SUBTYPE
            }

        val emojiSubtype: RichInputMethodSubtype
            get() {
                var emojiSubtype = sEmojiSubtype
                if (emojiSubtype == null) {
                    val rawEmojiSubtype = RichInputMethodManager.getInstance()
                            .findSubtypeByLocaleAndKeyboardLayoutSet(
                                    SubtypeLocaleUtils.NO_LANGUAGE, SubtypeLocaleUtils.EMOJI)
                    if (rawEmojiSubtype != null) {
                        emojiSubtype = RichInputMethodSubtype(rawEmojiSubtype)
                    }
                }
                if (emojiSubtype != null) {
                    sEmojiSubtype = emojiSubtype
                    return emojiSubtype
                }
                Log.w(TAG, "Can't find emoji subtype")
                Log.w(TAG, "No input method subtype found; returning dummy subtype: $DUMMY_EMOJI_SUBTYPE")
                return DUMMY_EMOJI_SUBTYPE
            }
    }
}
