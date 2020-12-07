package com.android.inputmethod.ui.personaldictionary.language.adapter

import com.android.inputmethod.ui.components.recycleradapter.Diffable

data class LanguageWordViewState(
        val languageId: Long,
        val displayName: String,
        val language: String,
        val country: String?,
        val variant: String?
) : Diffable {
    override fun isSameAs(other: Diffable): Boolean {
        if(other is LanguageWordViewState){
            return other.languageId == languageId
        }
        return false
    }
}