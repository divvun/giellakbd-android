package com.android.inputmethod.ui.components.recycleradapter

import android.content.Context
import android.view.View

interface BaseViewHolderFactory<Item, VH : BaseViewHolder<Item, ItemView>, ItemView : View> {
    fun createView(context: Context): ItemView

    fun createViewHolder(view: ItemView): VH
}