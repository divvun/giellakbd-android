package com.android.inputmethod.ui.personaldictionary.addworddialog

import io.reactivex.Observable


interface AddWordDialogView {
    val languageId: Long

    fun render(viewState: AddWordDialogViewState)
    fun events(): Observable<AddWordDialogEvent>
    fun dismiss()
}


