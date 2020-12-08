package com.android.inputmethod.ui.personaldictionary.language

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isInvisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.android.inputmethod.latin.databinding.FragmentPersonalLanguagesBinding
import com.android.inputmethod.ui.components.recycleradapter.EventAdapter
import com.android.inputmethod.ui.personaldictionary.dictionary.DictionaryNavArg
import com.android.inputmethod.ui.personaldictionary.language.adapter.LanguageWordEvent
import com.android.inputmethod.ui.personaldictionary.language.adapter.LanguageWordViewHolder
import com.android.inputmethod.usecases.LanguagesUseCase
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import no.divvun.dictionary.personal.PersonalDictionaryDatabase

class LanguageFragment : Fragment(), LanguageView {
    private lateinit var disposable: Disposable

    private lateinit var presenter: LanguagePresenter

    private lateinit var binding: FragmentPersonalLanguagesBinding

    private val factory = LanguageWordViewHolder.LanguageWordViewHolderFactory
    private val adapter = EventAdapter(factory)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val database = PersonalDictionaryDatabase.getInstance(context!!)
        val languageUseCase = LanguagesUseCase(database)
        presenter = LanguagePresenter(this, languageUseCase, requireContext())
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
            FragmentPersonalLanguagesBinding.inflate(inflater, container, false).also {
                binding = it
            }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvPersonallangLanguages.layoutManager = LinearLayoutManager(context!!)
        binding.rvPersonallangLanguages.adapter = adapter
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
        binding.gPersonallangEmpty.isInvisible = viewState.languages.isNotEmpty()
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
