package com.android.inputmethod.ui.personaldictionary.dictionary.adapter


sealed class DictionaryWordEvent {
    data class PressEvent(val wordId: Long, val word: String) : DictionaryWordEvent()
}