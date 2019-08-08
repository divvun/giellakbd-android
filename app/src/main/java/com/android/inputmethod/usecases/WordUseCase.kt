package com.android.inputmethod.usecases

import io.reactivex.Observable
import no.divvun.dictionary.personal.DictionaryWord
import no.divvun.dictionary.personal.PersonalDictionaryDatabase
import no.divvun.dictionary.personal.WordContext

class WordUseCase(private val database: PersonalDictionaryDatabase) {

    fun execute(wordId: Long): Observable<DictionaryWord> {
        return database.dictionaryDao()
                .findWordO(wordId)
    }
}