package com.android.inputmethod.usecases

import no.divvun.dictionary.personal.Language
import no.divvun.dictionary.personal.PersonalDictionaryDatabase

class CreateLanguageUseCase(val database: PersonalDictionaryDatabase) {
    fun execute(language: Language) {
        database.dictionaryDao().insertLanguage(language)
    }
}