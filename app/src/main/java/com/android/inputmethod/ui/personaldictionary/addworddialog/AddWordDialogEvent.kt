package com.android.inputmethod.ui.personaldictionary.addworddialog

sealed class AddWordDialogEvent {
    data class OnDialogInput(val word: String) : AddWordDialogEvent()
    data class OnDialogAddWordEvent(val word: String) : AddWordDialogEvent()
}
