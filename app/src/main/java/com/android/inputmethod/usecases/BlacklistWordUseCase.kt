package com.android.inputmethod.usecases

import arrow.core.Either
import arrow.core.left
import com.lenguyenthanh.rxarrow.z
import io.reactivex.Single
import no.divvun.dictionary.personal.DictionaryWord
import no.divvun.dictionary.personal.PersonalDictionaryDatabase

class BlacklistWordUseCase(
        private val database: PersonalDictionaryDatabase,
        private val validateWordUseCase: ValidateWordUseCase) {

    fun execute(languageId: Long, word: String): Single<Either<AddBlacklistWordException, AddBlacklistWordSuccess>> {
        return validateWordUseCase.execute(word)
                .flatMap { validationE ->
                    validationE.fold({
                        Single.just(AddBlacklistWordException.Validation(it).left())
                    }, {
                        database.dictionaryDao()
                                .findWordS(languageId, word)
                                .map {
                                    it.firstOrNull()?.copy(blacklisted = true)
                                            ?: DictionaryWord(word, 0, manuallyAdded = true, blacklisted = true, languageId = languageId)
                                }.flatMap {
                                    database.dictionaryDao()
                                            .upsertWord(it)
                                            .toSingle { AddBlacklistWordSuccess }
                                            .z {
                                                AddBlacklistWordException.Unknown(it)
                                            }
                                }
                    })
                }
    }
}


sealed class AddBlacklistWordException {
    class Validation(val validationException: ValidationException) : AddBlacklistWordException()
    data class Unknown(val cause: Throwable) : AddBlacklistWordException()
}

object AddBlacklistWordSuccess