package com.android.inputmethod.ui.personaldictionary.word


sealed class WordEvent {
    data class DeleteContext(val contextId: Long) : WordEvent()
}
