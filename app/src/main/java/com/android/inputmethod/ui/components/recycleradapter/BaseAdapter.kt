package com.android.inputmethod.ui.components.recycleradapter

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView

abstract class BaseAdapter<Item : Diffable, VH : BaseViewHolder<Item, ItemView>, ItemView : View>(protected var factory: BaseViewHolderFactory<Item, VH, ItemView>) :
    RecyclerView.Adapter<BaseViewHolder<Item, ItemView>>() {

    val items = mutableListOf<Item>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = factory.createView(parent.context)
        return factory.createViewHolder(view)
    }

    override fun getItemCount(): Int {
        return items.size
    }

    open fun update(newItems: List<Item>) {
        val diffCallback = DiffUtilsCallback(ArrayList(items), newItems)
        val result = DiffUtil.calculateDiff(diffCallback)

        this.items.clear()
        this.items.addAll(newItems)
        result.dispatchUpdatesTo(this)
    }
}