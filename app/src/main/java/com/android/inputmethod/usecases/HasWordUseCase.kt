package com.android.inputmethod.usecases

import io.reactivex.Single
import no.divvun.dictionary.personal.PersonalDictionaryDatabase

class HasWordUseCase(private val languageId: Long, private val database: PersonalDictionaryDatabase) {
    fun execute(word: String): Single<Boolean> {
        return database.dictionaryDao().findWordS(languageId, word).map { it.isNotEmpty() }
    }
}