package com.android.inputmethod.ui.personaldictionary.addworddialog

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
import com.android.inputmethod.usecases.AddWordUseCase
import com.android.inputmethod.usecases.ValidateWordUseCase
import com.jakewharton.rxbinding3.view.clicks
import com.jakewharton.rxbinding3.widget.editorActions
import com.jakewharton.rxbinding3.widget.textChanges
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import kotlinx.android.synthetic.main.dialog_add_word.*
import no.divvun.dictionary.personal.PersonalDictionaryDatabase


class AddWordDialogFragment : DialogFragment(), AddWordDialogView {

    private lateinit var disposable: Disposable
    private lateinit var presenter: AddWordDialogPresenter

    private val navArg by navArgs<AddWordDialogFragmentArgs>()
    override val languageId: Long by lazy { navArg.addWordDialogNavArg.languageId }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, android.R.style.Theme_Material_Light_Dialog_MinWidth)
        val database = PersonalDictionaryDatabase.getInstance(context!!)
        val validateWordUseCase = ValidateWordUseCase()
        val addWordUseCase = AddWordUseCase(database, validateWordUseCase)
        presenter = AddWordDialogPresenter(this, addWordUseCase, validateWordUseCase)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        dialog?.setTitle(R.string.addword_dialog_title)
        return inflater.inflate(R.layout.dialog_add_word, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        b_addword_cancel.setOnClickListener { dismiss() }
    }


    override fun render(viewState: AddWordDialogViewState) {
        til_addword.error = viewState.error?.asString(resources)
    }

    private fun AddWordViewError.asString(resources: Resources): String = when (this) {
        AddWordViewError.WordContainsSpace -> resources.getString(R.string.word_error_contains_space)
        AddWordViewError.EmptyWord -> resources.getString(R.string.word_error_empty)
        AddWordViewError.WordAlreadyExists -> resources.getString(R.string.addword_word_exists)
        AddWordViewError.Blacklisted -> resources.getString(R.string.addword_word_blacklisted)
        is AddWordViewError.Unknown -> this.message
    }

    override fun onStart() {
        super.onStart()
        tiet_addword.showSoftKeyboard()
    }

    override fun onResume() {
        super.onResume()
        disposable = presenter.start().subscribe(::render)
    }

    override fun onPause() {
        super.onPause()
        disposable.dispose()
    }

    override fun events(): Observable<AddWordDialogEvent> {
        return Observable.merge(
                tiet_addword.textChanges().skipInitialValue().map { AddWordDialogEvent.OnDialogInput(it.toString()) },
                b_addword_ok.clicks().map { AddWordDialogEvent.OnDialogAddWordEvent(tiet_addword.text.toString()) },
                tiet_addword.editorActions { it == EditorInfo.IME_ACTION_DONE }.map { AddWordDialogEvent.OnDialogAddWordEvent(tiet_addword.text.toString()) }
        )
    }

}