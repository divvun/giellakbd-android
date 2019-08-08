package com.android.inputmethod.ui.components.recycleradapter

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import timber.log.Timber

class EventAdapter<
    Item : Diffable,
    VH : BaseViewHolder<Item, ItemView>,
    ItemView,
    ItemEvent>
    (factory: BaseViewHolderFactory<Item, VH, ItemView>) :
    BaseAdapter<Item, VH, ItemView>(factory) where ItemView : ItemEventEmitter<ItemEvent>, ItemView : View {

    private val itemViewEventSubject = PublishSubject.create<ItemEvent>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = factory.createView(parent.context)
        return factory.createViewHolder(view)
    }

    override fun onBindViewHolder(holder: BaseViewHolder<Item, ItemView>, position: Int) {
        val item = items[position]
        holder.bind(item)
        holder.view.events().subscribe(itemViewEventSubject)
    }

    override fun onBindViewHolder(holder: BaseViewHolder<Item, ItemView>, position: Int, payloads: List<Any>) {
        Timber.d("onBindViewHolder | Pos: $position , Holder: $holder , Payloads: $payloads")
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position)
        } else {
            holder.applyUpdate(items[position], payloads)
            holder.view.events().subscribe(itemViewEventSubject)
        }
    }

    override fun update(newItems: List<Item>) {
        val diffCallback = DiffUtilsCallback(ArrayList(items), newItems)
        val result = DiffUtil.calculateDiff(diffCallback)

        this.items.clear()
        this.items.addAll(newItems)
        result.dispatchUpdatesTo(this)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        itemViewEventSubject.onComplete()
    }

    fun events(): Observable<ItemEvent> {
        return itemViewEventSubject.hide()
    }
}
