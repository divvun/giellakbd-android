package com.android.inputmethod.ui.personaldictionary.dictionary

import io.reactivex.Observable

interface DictionaryView {
    val languageId: Long

    fun render(viewState: DictionaryViewState)

    val events: Observable<DictionaryEvent>

    fun navigateToWordFragment(wordId: Long, word: String)
    fun navigateToAddWordDialogFragment(languageId: Long)
    fun navigateToUploadDictionary(languageId: Long)
    fun navigateToBlacklistFragment(languageId: Long)
}

