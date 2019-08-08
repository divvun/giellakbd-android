package com.android.inputmethod.usecases

import no.divvun.dictionary.personal.PersonalDictionaryDatabase

class RemoveWordContextUseCase(val database: PersonalDictionaryDatabase) {
    fun execute(wordContextId: Long) {
        database.dictionaryDao()
                .removeContext(wordContextId)
    }
}

