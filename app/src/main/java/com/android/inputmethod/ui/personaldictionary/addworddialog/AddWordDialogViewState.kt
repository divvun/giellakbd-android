package com.android.inputmethod.ui.personaldictionary.addworddialog

import com.android.inputmethod.usecases.AddWordException
import com.android.inputmethod.usecases.ValidationException

data class AddWordDialogViewState(
        val addWordEnabled: Boolean = false,
        val error: AddWordViewError? = null
)

sealed class AddWordViewError {
    object WordContainsSpace : AddWordViewError()
    object EmptyWord : AddWordViewError()
    object WordAlreadyExists : AddWordViewError()
    object Blacklisted : AddWordViewError()
    data class Unknown(val message: String) : AddWordViewError()
}

fun AddWordException.toAddWordError(): AddWordViewError =
        when (this) {
            is AddWordException.Validation -> validationException.toAddWordError()
            is AddWordException.WordAlreadyExists -> AddWordViewError.WordAlreadyExists
            is AddWordException.Unknown -> AddWordViewError.Unknown(cause.message
                    ?: "Unknown error")
            AddWordException.WordBlacklisted -> AddWordViewError.Blacklisted
        }

fun ValidationException.toAddWordError(): AddWordViewError = when (this) {
    is ValidationException.WordContainsSpace -> AddWordViewError.WordContainsSpace
    ValidationException.EmptyWord -> AddWordViewError.EmptyWord
}
