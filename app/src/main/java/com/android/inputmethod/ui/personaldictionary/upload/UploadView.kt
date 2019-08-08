package com.android.inputmethod.ui.personaldictionary.upload

import io.reactivex.Observable

interface UploadView {
    fun render(viewState: UploadViewState)
    fun events(): Observable<UploadEvent>
    fun navigateToSuccess()
}

