package com.android.inputmethod.usecases

import io.reactivex.Observable
import no.divvun.dictionary.personal.Dictionary
import no.divvun.dictionary.personal.Language
import no.divvun.dictionary.personal.PersonalDictionaryDatabase


class LanguagesUseCase(private val database: PersonalDictionaryDatabase) {
    fun execute(): Observable<List<Language>> {
        return database.dictionaryDao().languagesO()
    }
}