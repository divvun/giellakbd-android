package com.android.inputmethod.ui.personaldictionary.language

import io.reactivex.Observable

interface LanguageView {
    fun render(viewState: LanguageViewState)
    fun events(): Observable<LanguageEvent>

    fun navigateToDictionary(languageId: Long, language: String)
}

