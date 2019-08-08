package com.android.inputmethod.ui.personaldictionary.blacklist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isInvisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.inputmethod.latin.R
import com.android.inputmethod.ui.components.recycleradapter.*
import com.android.inputmethod.ui.getHtmlSpannedString
import com.android.inputmethod.ui.personaldictionary.blacklist.adapter.BlacklistWordViewHolder
import com.android.inputmethod.ui.personaldictionary.blacklistworddialog.BlacklistWordDialogNavArg
import com.android.inputmethod.usecases.BlacklistUseCase
import com.android.inputmethod.usecases.SetBlacklistUseCase
import com.android.inputmethod.usecases.SoftDeleteWordUseCase
import com.elevate.rxbinding3.swipes
import com.google.android.material.snackbar.Snackbar
import com.rawa.recyclerswipes.RecyclerSwipes
import com.rawa.recyclerswipes.SwipeDirection
import com.rawa.recyclerswipes.attachTo
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.subjects.PublishSubject
import kotlinx.android.synthetic.main.fragment_personal_blacklist.*
import no.divvun.dictionary.personal.PersonalDictionaryDatabase


class BlacklistFragment : Fragment(), BlacklistView {
    private lateinit var rvBlacklist: RecyclerView
    private lateinit var disposable: Disposable
    private lateinit var viewDisposable: Disposable

    private lateinit var presenter: BlacklistPresenter

    private val factory = BlacklistWordViewHolder.BlacklistWordViewHolderFactory
    private val adapter = EventAdapter(factory)

    private val navArgs by navArgs<BlacklistFragmentArgs>()
    override val languageId by lazy { navArgs.blacklistNavArg.languageId }

    override val events = PublishSubject.create<BlacklistEvent>()

    private lateinit var swipes: RecyclerSwipes
    private lateinit var snackbar: Snackbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        val database = PersonalDictionaryDatabase.getInstance(context!!)
        val blacklistUseCase = BlacklistUseCase(database)
        val removeWordUseCase = SoftDeleteWordUseCase(database)
        val blacklistWordUseCase = SetBlacklistUseCase(database)
        presenter = BlacklistPresenter(this, blacklistUseCase, removeWordUseCase, blacklistWordUseCase)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_personal_blacklist, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        rvBlacklist = rv_blacklist_words
        rvBlacklist.layoutManager = LinearLayoutManager(context!!)
        rvBlacklist.adapter = adapter

        fab_blacklist_addword.setOnClickListener {
            navigateToBlacklistWordDialogFragment(languageId)
        }

        swipes = RecyclerSwipes(
                SwipeDirection.LEFT to R.layout.swipe_left_allow,
                SwipeDirection.RIGHT to R.layout.swipe_right_delete
        )
        swipes.attachTo(rvBlacklist)

        snackbar = Snackbar.make(view, "", Snackbar.LENGTH_INDEFINITE)
        viewDisposable = events().subscribe { events.onNext(it) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewDisposable.dispose()
    }

    override fun onResume() {
        super.onResume()
        disposable = presenter.states.observeOn(AndroidSchedulers.mainThread()).subscribe(::render)
    }

    override fun onPause() {
        super.onPause()
        snackbar.dismiss()
        disposable.dispose()
    }

    override fun navigateToBlacklistWordDialogFragment(languageId: Long) {
        findNavController().navigate(BlacklistFragmentDirections.actionBlacklistFragmentToBlacklistWordDialogFragment(
                BlacklistWordDialogNavArg(languageId)
        ))
    }

    override fun render(viewState: BlacklistViewState) {
        adapter.update(viewState.blacklist)
        g_blacklist_empty.isInvisible = viewState.blacklist.isNotEmpty()
        g_blacklist_empty.requestLayout()
        renderSnackbar(viewState.snackbar)
    }

    private fun renderSnackbar(viewState: BlacklistSnackbarViewState) {
        when (viewState) {
            is BlacklistSnackbarViewState.WordRemoved -> {
                snackbar.setText(getHtmlSpannedString(R.string.snackbar_allow_word, viewState.word))
                snackbar.setAction(R.string.snackbar_undo) { undoRemove(viewState.wordId) }
                snackbar.show()
            }
            is BlacklistSnackbarViewState.RemoveFailed -> {
                snackbar.setText(getString(R.string.snackbar_delete_word_failed, viewState.wordException))
                snackbar.setAction(null, null)
                snackbar.show()
                adapter.notifyDataSetChanged()
            }
            is BlacklistSnackbarViewState.Hidden -> {
                snackbar.dismiss()
            }
            is BlacklistSnackbarViewState.WordAllowed -> {
                snackbar.setText(getHtmlSpannedString(R.string.snackbar_delete_word, viewState.word))
                snackbar.setAction(R.string.snackbar_undo) { undoAllow(viewState.wordId) }
                snackbar.show()
            }
            is BlacklistSnackbarViewState.AllowFailed -> {
                snackbar.setText(getString(R.string.snackbar_allow_word_failed, viewState.blacklistException))
                snackbar.setAction(null, null)
                snackbar.show()
                adapter.notifyDataSetChanged()
            }
        }
    }

    private fun undoRemove(wordId: Long) {
        events.onNext(BlacklistEvent.OnUndoRemove(wordId))
    }

    private fun undoAllow(wordId: Long) {
        events.onNext(BlacklistEvent.OnUndoAllow(wordId))
    }

    private fun events(): Observable<BlacklistEvent> {
        return swipes.swipes().flatMap {
            when (it.direction) {
                SwipeDirection.LEFT -> {
                    val word = adapter.items[it.viewHolder.adapterPosition]
                    Observable.just(BlacklistEvent.OnAllowEvent(word.wordId, word.word))
                }
                SwipeDirection.RIGHT -> {
                    val word = adapter.items[it.viewHolder.adapterPosition]
                    Observable.just(BlacklistEvent.OnRemoveEvent(word.wordId, word.word))
                }
                else -> Observable.empty()
            }
        }
    }
}
