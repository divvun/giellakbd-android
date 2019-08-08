package com.android.inputmethod.ui.personaldictionary.dictionary

sealed class DictionaryEvent {
    data class OnWordSelected(val wordId: Long, val word: String) : DictionaryEvent()
    data class OnRemoveEvent(val wordId: Long, val word: String) : DictionaryEvent()
    data class OnBlacklistEvent(val wordId: Long, val word: String) : DictionaryEvent()
    data class OnUndoRemoveEvent(val wordId: Long) : DictionaryEvent()
    data class OnUndoBlacklistEvent(val wordId: Long) : DictionaryEvent()
}