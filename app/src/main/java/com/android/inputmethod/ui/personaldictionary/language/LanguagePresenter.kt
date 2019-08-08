package com.android.inputmethod.ui.personaldictionary.language

import com.android.inputmethod.ui.personaldictionary.language.adapter.LanguageWordViewState
import com.android.inputmethod.usecases.LanguagesUseCase
import io.reactivex.Observable
import io.reactivex.ObservableTransformer
import no.divvun.dictionary.personal.Language
import java.util.*

class LanguagePresenter(
        private val view: LanguageView,
        private val languageUseCase: LanguagesUseCase
) {
    private val initialViewState: LanguageViewState = LanguageViewState()

    val states by lazy { start() }

    fun start(): Observable<LanguageViewState> {
        return Observable.merge(
                        languageUseCase.execute().map { LanguageUpdate.Lang(it) },
                        view.events().compose(uiTransformer))
                .scan(initialViewState, { state: LanguageViewState, event: LanguageUpdate ->
                    when (event) {
                        is LanguageUpdate.Lang -> {
                            state.copy(languages = event.languages.map {
                                LanguageWordViewState(it.languageId, it.language, it.country, it.variant)
                            }.sortedBy { it.language.toLowerCase(Locale.getDefault()) })
                        }
                    }
                })
                .replay(1)
                .autoConnect()
    }

    private val uiTransformer = ObservableTransformer<LanguageEvent, LanguageUpdate> {
        it.flatMap { languageEvent ->
            when (languageEvent) {
                is LanguageEvent.OnLanguageSelected -> {
                    view.navigateToDictionary(languageEvent.languageId, languageEvent.language)
                    Observable.empty<LanguageUpdate>()
                }
            }
        }
    }

}


sealed class LanguageUpdate {
    data class Lang(val languages: List<Language>) : LanguageUpdate()
}
