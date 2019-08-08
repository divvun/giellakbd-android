package com.android.inputmethod.ui.personaldictionary.blacklist

import io.reactivex.Observable

interface BlacklistView {
    val languageId: Long

    fun render(viewState: BlacklistViewState)
    val events: Observable<BlacklistEvent>

    fun navigateToBlacklistWordDialogFragment(languageId: Long)
}

