package com.android.inputmethod.ui.personaldictionary.blacklist

import com.android.inputmethod.ui.personaldictionary.blacklist.adapter.BlacklistWordViewState
import com.android.inputmethod.usecases.BlacklistWordException
import com.android.inputmethod.usecases.RemoveWordException

data class BlacklistViewState(
        val blacklist: List<BlacklistWordViewState> = emptyList(),
        val snackbar: BlacklistSnackbarViewState = BlacklistSnackbarViewState.Hidden
)

sealed class BlacklistSnackbarViewState(open val wordId: Long) {
    data class WordRemoved(override val wordId: Long, val word: String) : BlacklistSnackbarViewState(wordId)
    data class RemoveFailed(override val wordId: Long, val wordException: RemoveWordException) : BlacklistSnackbarViewState(wordId)
    data class WordAllowed(override val wordId: Long, val word: String) : BlacklistSnackbarViewState(wordId)
    data class AllowFailed(override val wordId: Long, val blacklistException: BlacklistWordException) : BlacklistSnackbarViewState(wordId)
    object Hidden : BlacklistSnackbarViewState(-1)
}
