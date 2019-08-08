package com.android.inputmethod.ui.personaldictionary.blacklist

sealed class BlacklistEvent {
    data class OnRemoveEvent(val wordId: Long, val word: String) : BlacklistEvent()
    data class OnAllowEvent(val wordId: Long, val word: String) : BlacklistEvent()
    data class OnUndoAllow(val wordId: Long) : BlacklistEvent()
    data class OnUndoRemove(val wordId: Long) : BlacklistEvent()
}