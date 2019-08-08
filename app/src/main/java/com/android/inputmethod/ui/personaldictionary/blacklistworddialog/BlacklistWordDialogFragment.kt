package com.android.inputmethod.ui.personaldictionary.blacklistworddialog

import android.content.res.Resources
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.DialogFragment
import androidx.navigation.fragment.navArgs
import com.android.inputmethod.latin.R
import com.android.inputmethod.ui.showSoftKeyboard
import com.android.inputmethod.usecases.BlacklistWordUseCase
import com.android.inputmethod.usecases.ValidateWordUseCase
import com.jakewharton.rxbinding3.view.clicks
import com.jakewharton.rxbinding3.widget.editorActions
import com.jakewharton.rxbinding3.widget.textChanges
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import kotlinx.android.synthetic.main.dialog_blacklist_word.*
import no.divvun.dictionary.personal.PersonalDictionaryDatabase


class BlacklistWordDialogFragment : DialogFragment(), BlacklistWordDialogView {

    private lateinit var disposable: Disposable
    private lateinit var presenter: BlacklistWordDialogPresenter

    private val navArg by navArgs<BlacklistWordDialogFragmentArgs>()
    override val languageId: Long by lazy { navArg.blacklistWordDialogNavArg.languageId }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, android.R.style.Theme_Material_Light_Dialog_MinWidth)
        val database = PersonalDictionaryDatabase.getInstance(context!!)
        val validateWordUseCase = ValidateWordUseCase()
        val blacklistWordUseCase = BlacklistWordUseCase(database, validateWordUseCase)
        presenter = BlacklistWordDialogPresenter(this, blacklistWordUseCase, validateWordUseCase)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        dialog?.setTitle(R.string.blacklistword_dialog_title)
        return inflater.inflate(R.layout.dialog_blacklist_word, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        b_blacklistword_cancel.setOnClickListener { dismiss() }
    }

    override fun render(viewState: BlacklistWordDialogViewState) {
        til_blacklistword.error = viewState.error?.asString(resources)
    }

    private fun BlacklistWordViewError.asString(resources: Resources): String = when (this) {
        BlacklistWordViewError.WordContainsSpace -> resources.getString(R.string.word_error_contains_space)
        BlacklistWordViewError.EmptyWord -> resources.getString(R.string.word_error_empty)
        is BlacklistWordViewError.Unknown -> this.message
    }

    override fun onStart() {
        super.onStart()
        tiet_blacklistword.showSoftKeyboard()
    }

    override fun onResume() {
        super.onResume()
        disposable = presenter.start().subscribe(::render)
    }

    override fun onPause() {
        super.onPause()
        disposable.dispose()
    }

    override fun events(): Observable<BlacklistWordDialogEvent> {
        return Observable.merge(
                tiet_blacklistword.textChanges().skipInitialValue().map { BlacklistWordDialogEvent.OnDialogInput(it.toString()) },
                b_blacklistword_ok.clicks().map { BlacklistWordDialogEvent.OnDialogBlacklistWordEvent(tiet_blacklistword.text.toString()) },
                tiet_blacklistword.editorActions { it == EditorInfo.IME_ACTION_DONE }.map { BlacklistWordDialogEvent.OnDialogBlacklistWordEvent(tiet_blacklistword.text.toString()) }
        )
    }

}