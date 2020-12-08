package com.android.inputmethod.ui.components.recycleradapter

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import timber.log.Timber

abstract class BaseViewHolder<Item, ItemView : View>(val view: ItemView) :
        RecyclerView.ViewHolder(view) {

    abstract fun bind(item: Item)

    fun applyUpdate(item: Item, payloads: List<Any>) {
        Timber.d("Apply update! Item: $item, $payloads")
    }
}