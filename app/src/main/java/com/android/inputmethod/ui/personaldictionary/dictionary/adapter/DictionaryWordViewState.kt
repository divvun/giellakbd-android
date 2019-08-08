package com.android.inputmethod.ui.personaldictionary.dictionary.adapter

import com.android.inputmethod.ui.components.recycleradapter.Diffable

data class DictionaryWordViewState(
        val wordId: Long,
        val typeCount: Long,
        val word: String
) : Diffable {
    override fun isSameAs(other: Diffable): Boolean {
        if(other is DictionaryWordViewState){
            return other.wordId == wordId
        }
        return false
    }
}