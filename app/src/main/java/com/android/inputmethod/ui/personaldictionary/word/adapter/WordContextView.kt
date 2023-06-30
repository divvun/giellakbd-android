package com.android.inputmethod.ui.personaldictionary.word.adapter

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.text.bold
import androidx.core.text.buildSpannedString
import com.android.inputmethod.latin.R
import com.android.inputmethod.latin.databinding.DictionaryItemContextBinding
import com.android.inputmethod.ui.components.recycleradapter.ItemEventEmitter
import io.reactivex.Observable

class WordContextView(context: Context, attr: AttributeSet?, style: Int) : ConstraintLayout(context, attr, style), ItemEventEmitter<WordContextEvent> {
    constructor(context: Context, attr: AttributeSet) : this(context, attr, 0)
    constructor(context: Context) : this(context, null, 0)

    private lateinit var viewState: WordContextViewState

    private val binding = DictionaryItemContextBinding.inflate(LayoutInflater.from(context), this, true)

    init {
        LayoutInflater.from(context).inflate(R.layout.dictionary_item_context, this)
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    }

    fun update(viewState: WordContextViewState) {
        this.viewState = viewState
        val text = buildSpannedString {
            append(viewState.prevWords.joinToString(" "))
            bold {
                append(" ${viewState.word} ")
            }
            append(viewState.nextWords.joinToString(" "))
        }
        binding.tvContextitemText.text = text
    }


    override fun events(): Observable<WordContextEvent> {
        return Observable.empty()
    }
}