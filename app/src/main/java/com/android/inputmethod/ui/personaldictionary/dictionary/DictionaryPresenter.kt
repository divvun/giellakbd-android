package com.android.inputmethod.ui.personaldictionary.dictionary

import com.android.inputmethod.ui.SNACKBAR_TIMEOUT_SECONDS
import com.android.inputmethod.ui.personaldictionary.dictionary.adapter.DictionaryWordViewState
import com.android.inputmethod.usecases.*
import io.reactivex.Observable
import io.reactivex.ObservableTransformer
import no.divvun.dictionary.personal.DictionaryWord
import java.util.*
import java.util.concurrent.TimeUnit

class DictionaryPresenter(
        private val view: DictionaryView,
        private val dictionaryUseCase: DictionaryUseCase,
        private val softDeleteWordUseCase: SoftDeleteWordUseCase,
        private val blacklistWordUseCase: SetBlacklistUseCase
) {
    private val initialViewState: DictionaryViewState = DictionaryViewState()

    val states by lazy { start() }

    fun start(): Observable<DictionaryViewState> {
        return Observable.merge(
                dictionaryUseCase.execute(view.languageId).map { DictionaryUpdate.Dictionary(it) },
                view.events.compose(uiTransformer))
                .scan(initialViewState, { state: DictionaryViewState, event: DictionaryUpdate ->
                    when (event) {
                        is DictionaryUpdate.Dictionary -> {
                            state.copy(dictionary = event.words.map {
                                DictionaryWordViewState(it.wordId, it.typeCount, it.word)
                            }.sortedBy { it.word.lowercase(Locale.getDefault()) })
                        }
                        is DictionaryUpdate.WordRemoved ->
                            state.copy(
                                    snackbar = SnackbarViewState.WordRemoved(event.wordId, event.word)
                            )
                        is DictionaryUpdate.FailedToRemoveWord -> {
                            state.copy(
                                    snackbar = SnackbarViewState.RemoveFailed(event.wordId, event.removeWordException)
                            )
                        }
                        is DictionaryUpdate.WordBlacklisted ->
                            state.copy(snackbar = SnackbarViewState.WordBlacklisted(event.wordId, event.word))
                        is DictionaryUpdate.FailedToBlacklistWord -> state.copy(
                                snackbar = SnackbarViewState.BlacklistFailed(event.wordId, event.blacklistException)
                        )
                        is DictionaryUpdate.SnackbarTimedOut -> {
                            if (state.snackbar.wordId == event.wordId) {
                                state.copy(snackbar = SnackbarViewState.Hidden)
                            } else {
                                state
                            }
                        }
                    }
                })
                .distinctUntilChanged()
                .replay(1)
                .autoConnect()
    }

    private val uiTransformer = ObservableTransformer<DictionaryEvent, DictionaryUpdate> { it ->
        it.flatMap { event ->
            when (event) {
                is DictionaryEvent.OnWordSelected -> {
                    view.navigateToWordFragment(event.wordId, event.word)
                    Observable.empty<DictionaryUpdate>()
                }
                is DictionaryEvent.OnRemoveEvent -> {
                    softDeleteWordUseCase.execute(event.wordId, true).map { removeWordResult ->
                        removeWordResult.fold({
                            DictionaryUpdate.FailedToRemoveWord(it, event.wordId, event.word)
                        }, {
                            DictionaryUpdate.WordRemoved(event.wordId, event.word)
                        })
                    }.toObservable().flatMap {
                        Observable.concat(
                                Observable.just(it),
                                Observable.timer(SNACKBAR_TIMEOUT_SECONDS, TimeUnit.SECONDS).map { DictionaryUpdate.SnackbarTimedOut(event.wordId) }
                        )
                    }
                }
                is DictionaryEvent.OnBlacklistEvent -> {
                    blacklistWordUseCase.execute(event.wordId, true).map { blacklistWordResult ->
                        blacklistWordResult.fold({
                            DictionaryUpdate.FailedToBlacklistWord(it, event.wordId, event.word)
                        }, {
                            DictionaryUpdate.WordBlacklisted(event.wordId, event.word)
                        })
                    }.toObservable().flatMap {
                        Observable.concat(
                                Observable.just(it),
                                Observable.timer(SNACKBAR_TIMEOUT_SECONDS, TimeUnit.SECONDS).map { DictionaryUpdate.SnackbarTimedOut(event.wordId) }
                        )
                    }
                }
                is DictionaryEvent.OnUndoRemoveEvent -> {
                    softDeleteWordUseCase.execute(event.wordId, false).toObservable().flatMap {
                        // For now just ignore
                        Observable.empty<DictionaryUpdate>()
                    }
                }
                is DictionaryEvent.OnUndoBlacklistEvent ->
                    blacklistWordUseCase.execute(event.wordId, false).toObservable().flatMap {
                        // For now just ignore
                        Observable.empty<DictionaryUpdate>()
                    }
            }
        }
    }
}

sealed class DictionaryUpdate {
    data class Dictionary(val words: List<DictionaryWord>) : DictionaryUpdate()
    data class WordRemoved(val wordId: Long, val word: String) : DictionaryUpdate()
    data class FailedToRemoveWord(val removeWordException: RemoveWordException, val wordId: Long, val word: String) : DictionaryUpdate()
    data class SnackbarTimedOut(val wordId: Long) : DictionaryUpdate()
    data class WordBlacklisted(val wordId: Long, val word: String) : DictionaryUpdate()
    data class FailedToBlacklistWord(val blacklistException: BlacklistWordException, val wordId: Long, val word: String) : DictionaryUpdate()
}
