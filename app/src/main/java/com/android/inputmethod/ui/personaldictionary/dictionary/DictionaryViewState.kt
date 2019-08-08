package com.android.inputmethod.ui.personaldictionary.dictionary

import com.android.inputmethod.ui.personaldictionary.dictionary.adapter.DictionaryWordViewState
import com.android.inputmethod.usecases.BlacklistWordException
import com.android.inputmethod.usecases.RemoveWordException

data class DictionaryViewState(
        val dictionary: List<DictionaryWordViewState> = emptyList(),
        val snackbar: SnackbarViewState = SnackbarViewState.Hidden
)

sealed class SnackbarViewState(open val wordId: Long) {
    data class WordRemoved(override val wordId: Long, val word: String) : SnackbarViewState(wordId)
    data class RemoveFailed(override val wordId: Long, val wordException: RemoveWordException) : SnackbarViewState(wordId)
    data class WordBlacklisted(override val wordId: Long, val word: String) : SnackbarViewState(wordId)
    data class BlacklistFailed(override val wordId: Long, val blacklistException: BlacklistWordException) : SnackbarViewState(wordId)
    object Hidden : SnackbarViewState(-1)
}
