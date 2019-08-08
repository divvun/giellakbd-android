package com.android.inputmethod.ui.personaldictionary.blacklistworddialog

sealed class BlacklistWordDialogEvent {
    data class OnDialogInput(val word: String) : BlacklistWordDialogEvent()
    data class OnDialogBlacklistWordEvent(val word: String) : BlacklistWordDialogEvent()
}
