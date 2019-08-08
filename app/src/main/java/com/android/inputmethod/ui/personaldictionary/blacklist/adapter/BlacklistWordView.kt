package com.android.inputmethod.ui.personaldictionary.blacklist.adapter

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.constraintlayout.widget.ConstraintLayout
import com.android.inputmethod.latin.R
import com.android.inputmethod.ui.components.recycleradapter.ItemEventEmitter
import com.jakewharton.rxbinding3.view.clicks
import io.reactivex.Observable
import kotlinx.android.synthetic.main.blacklist_item.view.*

class BlacklistWordView(context: Context, attr: AttributeSet?, style: Int) : ConstraintLayout(context, attr, style), ItemEventEmitter<BlacklistWordEvent> {
    constructor(context: Context, attr: AttributeSet) : this(context, attr, 0)
    constructor(context: Context) : this(context, null, 0)

    private lateinit var viewState: BlacklistWordViewState

    init {
        LayoutInflater.from(context).inflate(R.layout.blacklist_item, this)
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    }

    fun update(viewState: BlacklistWordViewState) {
        this.viewState = viewState
        tv_blacklistitem_word.text = viewState.word
    }


    override fun events(): Observable<BlacklistWordEvent> {
        return Observable.empty()
    }
}