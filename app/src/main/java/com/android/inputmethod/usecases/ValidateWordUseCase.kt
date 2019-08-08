package com.android.inputmethod.usecases

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.reactivex.Single

class ValidateWordUseCase {

    fun execute(word: String): Single<Either<ValidationException, ValidationSuccess>> {
        return when {
            word.isEmpty() -> {
                Single.just(ValidationException.EmptyWord.left())
            }
            word.contains(' ') -> {
                Single.just(ValidationException.WordContainsSpace("Word '$word' contains a space").left())
            }
            else -> {
                Single.just(ValidationSuccess(word).right())
            }
        }
    }
}

sealed class ValidationException {
    data class WordContainsSpace(val message: String) : ValidationException()
    object EmptyWord : ValidationException()
}

data class ValidationSuccess(val word: String)
