package com.android.inputmethod.ui.personaldictionary.word

import com.android.inputmethod.ui.personaldictionary.word.WordUpdate.WordContext
import com.android.inputmethod.ui.personaldictionary.word.adapter.WordContextViewState
import com.android.inputmethod.usecases.RemoveWordContextUseCase
import com.android.inputmethod.usecases.WordContextUseCase
import io.reactivex.Observable
import io.reactivex.ObservableTransformer
import no.divvun.dictionary.personal.WordWithContext


class WordPresenter(
        private val view: WordView,
        private val wordContextUseCase: WordContextUseCase,
        private val deleteWordContextUseCase: RemoveWordContextUseCase) {

    private val initialViewState = WordViewState(emptyList())

    fun start(): Observable<WordViewState> {
        return Observable.merge(
                view.events().flatMap {
                    when (it) {
                        is WordEvent.DeleteContext -> {
                            deleteWordContextUseCase.execute(it.contextId)
                            Observable.empty<WordUpdate>()
                        }
                    }
                },
                wordContextUseCase.execute(view.wordId).compose(wordContextTransformer).map { WordContext(it) }
        ).scan(initialViewState, { state, event ->
            when (event) {
                is WordContext -> {
                    state.copy(contexts = event.contexts)
                }
            }
        })
    }

}

val wordContextTransformer: ObservableTransformer<WordWithContext, List<WordContextViewState>> =
        ObservableTransformer { it ->
            it.map { wordWithContext ->
                wordWithContext.contexts.map {
                    WordContextViewState(
                            it.wordContextId,
                            wordWithContext.dictionaryWord.word,
                            it.prevWords,
                            it.nextWords)
                }
            }
        }

sealed class WordUpdate {
    data class WordContext(
            val contexts: List<WordContextViewState>
    ) : WordUpdate()
}

