package com.android.inputmethod.ui.personaldictionary.language.adapter

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.constraintlayout.widget.ConstraintLayout
import com.android.inputmethod.latin.R
import com.android.inputmethod.ui.components.recycleradapter.ItemEventEmitter
import com.jakewharton.rxbinding3.view.clicks
import io.reactivex.Observable
import kotlinx.android.synthetic.main.language_item.view.*

class LanguageWordView(context: Context, attr: AttributeSet?, style: Int) : ConstraintLayout(context, attr, style), ItemEventEmitter<LanguageWordEvent> {
    constructor(context: Context, attr: AttributeSet) : this(context, attr, 0)
    constructor(context: Context) : this(context, null, 0)

    private lateinit var viewState: LanguageWordViewState

    init {
        LayoutInflater.from(context).inflate(R.layout.language_item, this)
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    }

    fun update(viewState: LanguageWordViewState) {
        this.viewState = viewState
        tv_langitem_lang.text = "${viewState.language} ${viewState.country} ${viewState.variant}"
    }


    override fun events(): Observable<LanguageWordEvent> {
        return tv_langitem_lang.clicks().map { LanguageWordEvent.PressEvent(viewState.languageId, viewState.language) }
    }
}