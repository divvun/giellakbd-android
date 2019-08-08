package com.android.inputmethod.ui.personaldictionary.blacklistworddialog

import com.android.inputmethod.usecases.AddBlacklistWordException
import com.android.inputmethod.usecases.ValidationException

data class BlacklistWordDialogViewState(
        val blacklistWordEnabled: Boolean = false,
        val error: BlacklistWordViewError? = null
)

sealed class BlacklistWordViewError {
    object WordContainsSpace : BlacklistWordViewError()
    object EmptyWord : BlacklistWordViewError()
    data class Unknown(val message: String) : BlacklistWordViewError()
}

fun AddBlacklistWordException.toBlacklistWordError(): BlacklistWordViewError =
        when (this) {
            is AddBlacklistWordException.Validation -> validationException.toBlacklistWordError()
            is AddBlacklistWordException.Unknown -> BlacklistWordViewError.Unknown(cause.message
                    ?: "Unknown error")
        }

fun ValidationException.toBlacklistWordError(): BlacklistWordViewError = when (this) {
    is ValidationException.WordContainsSpace -> BlacklistWordViewError.WordContainsSpace
    ValidationException.EmptyWord -> BlacklistWordViewError.EmptyWord
}
