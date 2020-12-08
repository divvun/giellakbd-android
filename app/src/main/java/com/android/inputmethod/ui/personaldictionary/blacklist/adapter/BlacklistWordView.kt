package com.android.inputmethod.ui.personaldictionary.blacklist.adapter

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.constraintlayout.widget.ConstraintLayout
import com.android.inputmethod.latin.databinding.BlacklistItemBinding
import com.android.inputmethod.ui.components.recycleradapter.ItemEventEmitter
import io.reactivex.Observable

class BlacklistWordView(context: Context, attr: AttributeSet?, style: Int) : ConstraintLayout(context, attr, style), ItemEventEmitter<BlacklistWordEvent> {
    constructor(context: Context, attr: AttributeSet) : this(context, attr, 0)
    constructor(context: Context) : this(context, null, 0)

    private lateinit var viewState: BlacklistWordViewState
    private val binding = BlacklistItemBinding.inflate(LayoutInflater.from(context), this)

    init {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    }

    fun update(viewState: BlacklistWordViewState) {
        this.viewState = viewState
        binding.tvBlacklistitemWord.text = viewState.word
    }


    override fun events(): Observable<BlacklistWordEvent> {
        return Observable.empty()
    }
}