package com.android.inputmethod.ui.personaldictionary.dictionary.adapter

import android.content.Context
import com.android.inputmethod.ui.components.recycleradapter.BaseViewHolder
import com.android.inputmethod.ui.components.recycleradapter.BaseViewHolderFactory


class DictionaryWordViewHolder(view: DictionaryWordView) : BaseViewHolder<DictionaryWordViewState, DictionaryWordView>(view) {
    override fun bind(item: DictionaryWordViewState) {
        view.update(item)
    }

    companion object DictionaryWordViewHolderFactory : BaseViewHolderFactory<DictionaryWordViewState, DictionaryWordViewHolder, DictionaryWordView> {
        override fun createViewHolder(view: DictionaryWordView) = DictionaryWordViewHolder(view)
        override fun createView(context: Context) = DictionaryWordView(context)
    }
}