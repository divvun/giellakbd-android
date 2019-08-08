package com.android.inputmethod.ui.personaldictionary.language.adapter

import android.content.Context
import com.android.inputmethod.ui.components.recycleradapter.BaseViewHolder
import com.android.inputmethod.ui.components.recycleradapter.BaseViewHolderFactory


class LanguageWordViewHolder(view: LanguageWordView) : BaseViewHolder<LanguageWordViewState, LanguageWordView>(view) {
    override fun bind(item: LanguageWordViewState) {
        view.update(item)
    }

    companion object LanguageWordViewHolderFactory : BaseViewHolderFactory<LanguageWordViewState, LanguageWordViewHolder, LanguageWordView> {
        override fun createViewHolder(view: LanguageWordView) = LanguageWordViewHolder(view)
        override fun createView(context: Context) = LanguageWordView(context)
    }
}