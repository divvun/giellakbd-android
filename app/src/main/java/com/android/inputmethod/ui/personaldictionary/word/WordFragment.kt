package com.android.inputmethod.ui.personaldictionary.word

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isInvisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.inputmethod.latin.R
import com.android.inputmethod.ui.components.recycleradapter.EventAdapter
import com.android.inputmethod.ui.personaldictionary.word.adapter.WordContextViewHolder
import com.android.inputmethod.usecases.RemoveWordContextUseCase
import com.android.inputmethod.usecases.WordContextUseCase
import com.elevate.rxbinding3.swipes
import com.rawa.recyclerswipes.RecyclerSwipes
import com.rawa.recyclerswipes.SwipeDirection
import com.rawa.recyclerswipes.attachTo
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import kotlinx.android.synthetic.main.fragment_word.*
import no.divvun.dictionary.personal.PersonalDictionaryDatabase

class WordFragment : Fragment(), WordView {

    private lateinit var rvDictionary: RecyclerView
    private lateinit var disposable: Disposable
    private val factory = WordContextViewHolder.DictionaryWordViewHolderFactory
    private val adapter = EventAdapter(factory)

    private lateinit var database: PersonalDictionaryDatabase
    private lateinit var presenter: WordPresenter

    private val args by navArgs<WordFragmentArgs>()
    override val wordId by lazy { args.wordNavArg.wordId }

    private lateinit var swipeCallback: RecyclerSwipes

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = PersonalDictionaryDatabase.getInstance(context!!)

        val wordContextUseCase = WordContextUseCase(database)
        val deleteWordContextUseCase = RemoveWordContextUseCase(database)
        presenter = WordPresenter(this, wordContextUseCase, deleteWordContextUseCase)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
            inflater.inflate(R.layout.fragment_word, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        rvDictionary = rv_word_wordcontexts
        rvDictionary.layoutManager = LinearLayoutManager(context!!)
        rvDictionary.adapter = adapter

        swipeCallback = RecyclerSwipes(SwipeDirection.RIGHT to R.layout.swipe_right_delete)
        swipeCallback.attachTo(rvDictionary)
    }

    override fun onResume() {
        super.onResume()
        disposable = presenter.start().observeOn(AndroidSchedulers.mainThread()).subscribe(::render)
    }

    override fun onPause() {
        super.onPause()
        disposable.dispose()
    }

    override fun events(): Observable<WordEvent> {
        return swipeCallback.swipes().flatMap {
            when (it.direction) {
                SwipeDirection.RIGHT -> {
                    val wordContextId = adapter.items[it.viewHolder.adapterPosition].wordContextId
                    Observable.just(WordEvent.DeleteContext(wordContextId))
                }
                else -> {
                    Observable.empty()
                }
            }
        }
    }

    override fun render(viewState: WordViewState) {
        adapter.update(viewState.contexts)
        g_word_empty.isInvisible = viewState.contexts.isNotEmpty()
        g_word_empty.requestLayout()

    }
}

