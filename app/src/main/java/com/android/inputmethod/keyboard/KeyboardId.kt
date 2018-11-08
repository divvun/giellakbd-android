/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.inputmethod.latin.common.Constants.Subtype.ExtraValue.KEYBOARD_LAYOUT_SET

import android.text.InputType
import android.text.TextUtils
import android.view.inputmethod.EditorInfo

import com.android.inputmethod.compat.EditorInfoCompatUtils
import com.android.inputmethod.latin.RichInputMethodSubtype
import com.android.inputmethod.latin.utils.InputTypeUtils

import java.util.Arrays
import java.util.Locale

/**
 * Unique identifier for each keyboard type.
 */
class KeyboardId(val mElementId: Int, params: KeyboardLayoutSet.Params) {

    val mSubtype: RichInputMethodSubtype?
    val mWidth: Int
    val mHeight: Int
    val mMode: Int
    val mEditorInfo: EditorInfo?
    val mClobberSettingsKey: Boolean
    val mLanguageSwitchKeyEnabled: Boolean
    val mCustomActionLabel: String?
    val mHasShortcutKey: Boolean
    val mIsSplitLayout: Boolean

    private val mHashCode: Int

    val isAlphabetKeyboard: Boolean
        get() = isAlphabetKeyboard(mElementId)

    val isMultiLine: Boolean
        get() = mEditorInfo!!.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0

    val locale: Locale
        get() = mSubtype!!.locale

    init {
        mSubtype = params.mSubtype
        mWidth = params.mKeyboardWidth
        mHeight = params.mKeyboardHeight
        mMode = params.mMode
        mEditorInfo = params.mEditorInfo
        mClobberSettingsKey = params.mNoSettingsKey
        mLanguageSwitchKeyEnabled = params.mLanguageSwitchKeyEnabled
        mCustomActionLabel = if (mEditorInfo!!.actionLabel != null)
            mEditorInfo.actionLabel.toString()
        else
            null
        mHasShortcutKey = params.mVoiceInputKeyEnabled
        mIsSplitLayout = params.mIsSplitLayoutEnabled

        mHashCode = computeHashCode(this)
    }

    private fun equals(other: KeyboardId): Boolean {
        return if (other === this) true else other.mElementId == mElementId
                && other.mMode == mMode
                && other.mWidth == mWidth
                && other.mHeight == mHeight
                && other.passwordInput() == passwordInput()
                && other.mClobberSettingsKey == mClobberSettingsKey
                && other.mHasShortcutKey == mHasShortcutKey
                && other.mLanguageSwitchKeyEnabled == mLanguageSwitchKeyEnabled
                && other.isMultiLine == isMultiLine
                && other.imeAction() == imeAction()
                && TextUtils.equals(other.mCustomActionLabel, mCustomActionLabel)
                && other.navigateNext() == navigateNext()
                && other.navigatePrevious() == navigatePrevious()
                && other.mSubtype == mSubtype
                && other.mIsSplitLayout == mIsSplitLayout
    }

    fun navigateNext(): Boolean {
        return mEditorInfo!!.imeOptions and EditorInfo.IME_FLAG_NAVIGATE_NEXT != 0 || imeAction() == EditorInfo.IME_ACTION_NEXT
    }

    fun navigatePrevious(): Boolean {
        return mEditorInfo!!.imeOptions and EditorInfo.IME_FLAG_NAVIGATE_PREVIOUS != 0 || imeAction() == EditorInfo.IME_ACTION_PREVIOUS
    }

    fun passwordInput(): Boolean {
        val inputType = mEditorInfo!!.inputType
        return InputTypeUtils.isPasswordInputType(inputType) || InputTypeUtils.isVisiblePasswordInputType(inputType)
    }

    fun imeAction(): Int {
        return InputTypeUtils.getImeOptionsActionIdFromEditorInfo(mEditorInfo!!)
    }

    override fun equals(other: Any?): Boolean {
        if (other !is KeyboardId) {
            return false
        }

        return equals(other)
    }

    override fun hashCode(): Int {
        return mHashCode
    }

    override fun toString(): String {
        return String.format(Locale.ROOT, "[%s %s:%s %dx%d %s %s%s%s%s%s%s%s%s%s]",
                elementIdToName(mElementId),
                mSubtype!!.locale,
                mSubtype.getExtraValueOf(KEYBOARD_LAYOUT_SET),
                mWidth, mHeight,
                modeName(mMode),
                actionName(imeAction()),
                if (navigateNext()) " navigateNext" else "",
                if (navigatePrevious()) " navigatePrevious" else "",
                if (mClobberSettingsKey) " clobberSettingsKey" else "",
                if (passwordInput()) " passwordInput" else "",
                if (mHasShortcutKey) " hasShortcutKey" else "",
                if (mLanguageSwitchKeyEnabled) " languageSwitchKeyEnabled" else "",
                if (isMultiLine) " isMultiLine" else "",
                if (mIsSplitLayout) " isSplitLayout" else ""
        )
    }

    companion object {
        const val MODE_TEXT = 0
        const val MODE_URL = 1
        const val MODE_EMAIL = 2
        const val MODE_IM = 3
        const val MODE_PHONE = 4
        const val MODE_NUMBER = 5
        const val MODE_DATE = 6
        const val MODE_TIME = 7
        const val MODE_DATETIME = 8

        const val ELEMENT_ALPHABET = 0
        const val ELEMENT_ALPHABET_MANUAL_SHIFTED = 1
        const val ELEMENT_ALPHABET_AUTOMATIC_SHIFTED = 2
        const val ELEMENT_ALPHABET_SHIFT_LOCKED = 3
        const val ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED = 4
        const val ELEMENT_SYMBOLS = 5
        const val ELEMENT_SYMBOLS_SHIFTED = 6
        const val ELEMENT_PHONE = 7
        const val ELEMENT_PHONE_SYMBOLS = 8
        const val ELEMENT_NUMBER = 9
        const val ELEMENT_EMOJI_RECENTS = 10
        const val ELEMENT_EMOJI_CATEGORY1 = 11
        const val ELEMENT_EMOJI_CATEGORY2 = 12
        const val ELEMENT_EMOJI_CATEGORY3 = 13
        const val ELEMENT_EMOJI_CATEGORY4 = 14
        const val ELEMENT_EMOJI_CATEGORY5 = 15
        const val ELEMENT_EMOJI_CATEGORY6 = 16
        const val ELEMENT_EMOJI_CATEGORY7 = 17
        const val ELEMENT_EMOJI_CATEGORY8 = 18
        const val ELEMENT_EMOJI_CATEGORY9 = 19
        const val ELEMENT_EMOJI_CATEGORY10 = 20
        const val ELEMENT_EMOJI_CATEGORY11 = 21
        const val ELEMENT_EMOJI_CATEGORY12 = 22
        const val ELEMENT_EMOJI_CATEGORY13 = 23
        const val ELEMENT_EMOJI_CATEGORY14 = 24
        const val ELEMENT_EMOJI_CATEGORY15 = 25
        const val ELEMENT_EMOJI_CATEGORY16 = 26

        private fun computeHashCode(id: KeyboardId): Int {
            return Arrays.hashCode(arrayOf(id.mElementId, id.mMode, id.mWidth, id.mHeight,
                    id.passwordInput(), id.mClobberSettingsKey, id.mHasShortcutKey,
                    id.mLanguageSwitchKeyEnabled, id.isMultiLine, id.imeAction(),
                    id.mCustomActionLabel ?: "", id.navigateNext(), id.navigatePrevious(),
                    id.mSubtype ?: "", id.mIsSplitLayout))
        }

        private fun isAlphabetKeyboard(elementId: Int): Boolean {
            return elementId < ELEMENT_SYMBOLS
        }

        fun equivalentEditorInfoForKeyboard(a: EditorInfo?, b: EditorInfo?): Boolean {
            if (a == null && b == null) return true
            return if (a == null || b == null) false else a.inputType == b.inputType
                    && a.imeOptions == b.imeOptions
                    && TextUtils.equals(a.privateImeOptions, b.privateImeOptions)
        }

        fun elementIdToName(elementId: Int): String? {
            when (elementId) {
                ELEMENT_ALPHABET -> return "alphabet"
                ELEMENT_ALPHABET_MANUAL_SHIFTED -> return "alphabetManualShifted"
                ELEMENT_ALPHABET_AUTOMATIC_SHIFTED -> return "alphabetAutomaticShifted"
                ELEMENT_ALPHABET_SHIFT_LOCKED -> return "alphabetShiftLocked"
                ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED -> return "alphabetShiftLockShifted"
                ELEMENT_SYMBOLS -> return "symbols"
                ELEMENT_SYMBOLS_SHIFTED -> return "symbolsShifted"
                ELEMENT_PHONE -> return "phone"
                ELEMENT_PHONE_SYMBOLS -> return "phoneSymbols"
                ELEMENT_NUMBER -> return "number"
                ELEMENT_EMOJI_RECENTS -> return "emojiRecents"
                ELEMENT_EMOJI_CATEGORY1 -> return "emojiCategory1"
                ELEMENT_EMOJI_CATEGORY2 -> return "emojiCategory2"
                ELEMENT_EMOJI_CATEGORY3 -> return "emojiCategory3"
                ELEMENT_EMOJI_CATEGORY4 -> return "emojiCategory4"
                ELEMENT_EMOJI_CATEGORY5 -> return "emojiCategory5"
                ELEMENT_EMOJI_CATEGORY6 -> return "emojiCategory6"
                ELEMENT_EMOJI_CATEGORY7 -> return "emojiCategory7"
                ELEMENT_EMOJI_CATEGORY8 -> return "emojiCategory8"
                ELEMENT_EMOJI_CATEGORY9 -> return "emojiCategory9"
                ELEMENT_EMOJI_CATEGORY10 -> return "emojiCategory10"
                ELEMENT_EMOJI_CATEGORY11 -> return "emojiCategory11"
                ELEMENT_EMOJI_CATEGORY12 -> return "emojiCategory12"
                ELEMENT_EMOJI_CATEGORY13 -> return "emojiCategory13"
                ELEMENT_EMOJI_CATEGORY14 -> return "emojiCategory14"
                ELEMENT_EMOJI_CATEGORY15 -> return "emojiCategory15"
                ELEMENT_EMOJI_CATEGORY16 -> return "emojiCategory16"
                else -> return null
            }
        }

        fun modeName(mode: Int): String? {
            when (mode) {
                MODE_TEXT -> return "text"
                MODE_URL -> return "url"
                MODE_EMAIL -> return "email"
                MODE_IM -> return "im"
                MODE_PHONE -> return "phone"
                MODE_NUMBER -> return "number"
                MODE_DATE -> return "date"
                MODE_TIME -> return "time"
                MODE_DATETIME -> return "datetime"
                else -> return null
            }
        }

        fun actionName(actionId: Int): String {
            return if (actionId == InputTypeUtils.IME_ACTION_CUSTOM_LABEL)
                "actionCustomLabel"
            else
                EditorInfoCompatUtils.imeActionName(actionId)
        }
    }
}
