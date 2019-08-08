package com.android.inputmethod.ui.personaldictionary.blacklist.adapter

import android.content.Context
import com.android.inputmethod.ui.components.recycleradapter.BaseViewHolder
import com.android.inputmethod.ui.components.recycleradapter.BaseViewHolderFactory


class BlacklistWordViewHolder(view: BlacklistWordView) : BaseViewHolder<BlacklistWordViewState, BlacklistWordView>(view) {
    override fun bind(item: BlacklistWordViewState) {
        view.update(item)
    }

    companion object BlacklistWordViewHolderFactory : BaseViewHolderFactory<BlacklistWordViewState, BlacklistWordViewHolder, BlacklistWordView> {
        override fun createViewHolder(view: BlacklistWordView) = BlacklistWordViewHolder(view)
        override fun createView(context: Context) = BlacklistWordView(context)
    }
}