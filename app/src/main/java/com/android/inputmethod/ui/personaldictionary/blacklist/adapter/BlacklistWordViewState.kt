package com.android.inputmethod.ui.personaldictionary.blacklist.adapter

import com.android.inputmethod.ui.components.recycleradapter.Diffable

data class BlacklistWordViewState(
        val wordId: Long,
        val word: String
) : Diffable {
    override fun isSameAs(other: Diffable): Boolean {
        if (other is BlacklistWordViewState) {
            return other.wordId == wordId
        }
        return false
    }
}