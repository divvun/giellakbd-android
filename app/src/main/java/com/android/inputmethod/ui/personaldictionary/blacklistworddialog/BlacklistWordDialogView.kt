package com.android.inputmethod.ui.personaldictionary.blacklistworddialog

import io.reactivex.Observable


interface BlacklistWordDialogView {
    val languageId: Long

    fun render(viewState: BlacklistWordDialogViewState)
    fun events(): Observable<BlacklistWordDialogEvent>
    fun dismiss()
}


