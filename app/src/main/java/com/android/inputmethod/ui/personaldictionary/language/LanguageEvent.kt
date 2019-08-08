package com.android.inputmethod.ui.personaldictionary.language

sealed class LanguageEvent {
    data class OnLanguageSelected(val languageId: Long, val language: String) : LanguageEvent()
}