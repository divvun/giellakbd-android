package com.android.inputmethod.usecases

import no.divvun.dictionary.personal.Language
import no.divvun.dictionary.personal.PersonalDictionaryDatabase

class LanguageUseCase(val database: PersonalDictionaryDatabase) {
    fun execute(language: Language): Long {
        return database.dictionaryDao().findCreateLanguage(language)
    }
}