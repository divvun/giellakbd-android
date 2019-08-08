package com.android.inputmethod.ui.personaldictionary.language

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isInvisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.inputmethod.latin.R
import com.android.inputmethod.ui.components.recycleradapter.EventAdapter
import com.android.inputmethod.ui.personaldictionary.dictionary.DictionaryNavArg
import com.android.inputmethod.ui.personaldictionary.language.adapter.LanguageWordEvent
import com.android.inputmethod.ui.personaldictionary.language.adapter.LanguageWordViewHolder
import com.android.inputmethod.usecases.LanguagesUseCase
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import kotlinx.android.synthetic.main.fragment_personal_languages.*
import no.divvun.dictionary.personal.PersonalDictionaryDatabase

class LanguageFragment : Fragment(), LanguageView {
    private lateinit var rvLanguage: RecyclerView
    private lateinit var disposable: Disposable

    private lateinit var presenter: LanguagePresenter

    private val factory = LanguageWordViewHolder.LanguageWordViewHolderFactory
    private val adapter = EventAdapter(factory)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val database = PersonalDictionaryDatabase.getInstance(context!!)
        val languageUseCase = LanguagesUseCase(database)
        presenter = LanguagePresenter(this, languageUseCase)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_personal_languages, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        rvLanguage = rv_personallang_languages
        rvLanguage.layoutManager = LinearLayoutManager(context!!)
        rvLanguage.adapter = adapter

    }

    override fun onResume() {
        super.onResume()
        disposable = presenter.states.observeOn(AndroidSchedulers.mainThread()).subscribe(::render)
    }

    override fun onPause() {
        super.onPause()
        disposable.dispose()
    }

    override fun render(viewState: LanguageViewState) {
        adapter.update(viewState.languages)
        g_personallang_empty.isInvisible = viewState.languages.isNotEmpty()
    }

    override fun events(): Observable<LanguageEvent> {
        return adapter.events().map {
            when (it) {
                is LanguageWordEvent.PressEvent -> {
                    LanguageEvent.OnLanguageSelected(it.languageId, it.language)
                }
            }
        }
    }

    override fun navigateToDictionary(languageId: Long, language: String) {
        findNavController().navigate(LanguageFragmentDirections.actionLanguageFragmentToDictionaryFragment(DictionaryNavArg(languageId), language))
    }
}
