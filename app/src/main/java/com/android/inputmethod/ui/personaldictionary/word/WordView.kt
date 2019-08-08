package com.android.inputmethod.ui.personaldictionary.word

import io.reactivex.Observable

interface WordView {
    fun events(): Observable<WordEvent>
    fun render(viewState: WordViewState)
    val wordId: Long
}