package com.android.inputmethod.ui.personaldictionary.word.adapter

import android.content.Context
import com.android.inputmethod.ui.components.recycleradapter.BaseViewHolder
import com.android.inputmethod.ui.components.recycleradapter.BaseViewHolderFactory


class WordContextViewHolder(view: WordContextView) : BaseViewHolder<WordContextViewState, WordContextView>(view) {
    override fun bind(item: WordContextViewState) {
        view.update(item)
    }

    companion object DictionaryWordViewHolderFactory : BaseViewHolderFactory<WordContextViewState, WordContextViewHolder, WordContextView> {
        override fun createView(context: Context): WordContextView = WordContextView(context)
        override fun createViewHolder(view: WordContextView): WordContextViewHolder =
                WordContextViewHolder(view)
    }
}