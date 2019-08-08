package com.android.inputmethod.usecases

import arrow.core.Either
import arrow.core.left
import com.lenguyenthanh.rxarrow.z
import io.reactivex.Single
import no.divvun.dictionary.personal.DictionaryWord
import no.divvun.dictionary.personal.PersonalDictionaryDatabase

class AddWordUseCase(
        private val database: PersonalDictionaryDatabase,
        private val validateWordUseCase: ValidateWordUseCase) {

    fun execute(languageId: Long, word: String): Single<Either<AddWordException, AddWordSuccess>> {
        return validateWordUseCase.execute(word)
                .flatMap { validationE ->
                    validationE.fold({
                        Single.just(AddWordException.Validation(it).left())
                    }, {
                        database.dictionaryDao()
                                .findWordS(languageId, word)
                                .flatMap {
                                    if (it.isEmpty()) {
                                        database.dictionaryDao().insertWord(DictionaryWord(word, 0, true, languageId = languageId)).toSingle { AddWordSuccess }
                                                .z {
                                                    AddWordException.Unknown(it)
                                                }
                                    } else {
                                        val dictionaryWord = it.first()
                                        when {
                                            dictionaryWord.blacklisted -> {
                                                Single.just(AddWordException.WordBlacklisted.left())
                                            }
                                            dictionaryWord.softDeleted -> {
                                                database.dictionaryDao().upsertWord(DictionaryWord(word, 0, true, languageId = languageId)).toSingle { AddWordSuccess }.z {
                                                    AddWordException.Unknown(it)
                                                }
                                            }
                                            else -> {
                                                Single.just(AddWordException.WordAlreadyExists.left())
                                            }
                                        }

                                    }
                                }
                    })
                }
    }
}


sealed class AddWordException {
    class Validation(val validationException: ValidationException) : AddWordException()
    object WordAlreadyExists : AddWordException()
    object WordBlacklisted : AddWordException()
    data class Unknown(val cause: Throwable) : AddWordException()
}

object AddWordSuccess