package com.android.inputmethod.ui.personaldictionary.dictionary.adapter

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.constraintlayout.widget.ConstraintLayout
import com.android.inputmethod.latin.R
import com.android.inputmethod.latin.databinding.DictionaryItemBinding
import com.android.inputmethod.ui.components.recycleradapter.ItemEventEmitter
import com.jakewharton.rxbinding3.view.clicks
import io.reactivex.Observable

class DictionaryWordView(context: Context, attr: AttributeSet?, style: Int) : ConstraintLayout(context, attr, style), ItemEventEmitter<DictionaryWordEvent> {
    constructor(context: Context, attr: AttributeSet) : this(context, attr, 0)
    constructor(context: Context) : this(context, null, 0)

    private lateinit var viewState: DictionaryWordViewState
    private val binding = DictionaryItemBinding.inflate(LayoutInflater.from(context), this)

    init {
        LayoutInflater.from(context).inflate(R.layout.dictionary_item, this)
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    }

    fun update(viewState: DictionaryWordViewState) {
        this.viewState = viewState
        binding.tvDictitemWord.text = viewState.word
    }


    override fun events(): Observable<DictionaryWordEvent> {
        return binding.tvDictitemWord.clicks().map { DictionaryWordEvent.PressEvent(viewState.wordId, viewState.word) }
    }
}