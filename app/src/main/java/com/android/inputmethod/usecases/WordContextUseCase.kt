package com.android.inputmethod.usecases

import io.reactivex.Observable
import no.divvun.dictionary.personal.WordWithContext
import no.divvun.dictionary.personal.PersonalDictionaryDatabase

class WordContextUseCase(private val database: PersonalDictionaryDatabase) {

    fun execute(wordId: Long): Observable<WordWithContext> {
        return database.dictionaryDao()
                .wordWithContext(wordId)
    }
}