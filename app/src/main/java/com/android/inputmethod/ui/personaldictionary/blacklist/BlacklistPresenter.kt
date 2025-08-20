package com.android.inputmethod.ui.personaldictionary.blacklist

import com.android.inputmethod.ui.SNACKBAR_TIMEOUT_SECONDS
import com.android.inputmethod.ui.personaldictionary.blacklist.adapter.BlacklistWordViewState
import com.android.inputmethod.usecases.*
import io.reactivex.Observable
import io.reactivex.ObservableTransformer
import no.divvun.dictionary.personal.DictionaryWord
import java.util.*
import java.util.concurrent.TimeUnit

class BlacklistPresenter(
        private val view: BlacklistView,
        private val blacklistUseCase: BlacklistUseCase,
        private val softDeleteWordUseCase: SoftDeleteWordUseCase,
        private val blacklistWordUseCase: SetBlacklistUseCase
) {
    private val initialViewState: BlacklistViewState = BlacklistViewState()

    val states by lazy { start() }

    fun start(): Observable<BlacklistViewState> {
        return Observable.merge(
                blacklistUseCase.execute(view.languageId).map { BlacklistUpdate.Blacklist(it) },
                view.events.compose(uiTransformer))
                .scan(initialViewState, { state: BlacklistViewState, event: BlacklistUpdate ->
                    when (event) {
                        is BlacklistUpdate.Blacklist -> {
                            state.copy(blacklist = event.words.map {
                                BlacklistWordViewState(it.wordId, it.word)
                            }.sortedBy { it.word.lowercase(Locale.getDefault()) })
                        }
                        is BlacklistUpdate.WordRemoved ->
                            state.copy(
                                    snackbar = BlacklistSnackbarViewState.WordRemoved(event.wordId, event.word)
                            )
                        is BlacklistUpdate.FailedToRemoveWord -> {
                            state.copy(
                                    snackbar = BlacklistSnackbarViewState.RemoveFailed(event.wordId, event.removeWordException)
                            )
                        }
                        is BlacklistUpdate.AllowWord ->
                            state.copy(
                                    snackbar = BlacklistSnackbarViewState.WordAllowed(event.wordId, event.word)
                            )
                        is BlacklistUpdate.FailedToAllowWord -> {
                            state.copy(
                                    snackbar = BlacklistSnackbarViewState.AllowFailed(event.wordId, event.blacklistWordException)
                            )
                        }
                        is BlacklistUpdate.SnackbarTimedOut -> {
                            if (state.snackbar.wordId == event.wordId) {
                                state.copy(snackbar = BlacklistSnackbarViewState.Hidden)
                            } else {
                                state
                            }
                        }
                    }
                })
                .replay(1)
                .autoConnect()
    }

    private val uiTransformer = ObservableTransformer<BlacklistEvent, BlacklistUpdate> { it ->
        it.flatMap { event ->
            when (event) {
                is BlacklistEvent.OnAllowEvent -> {
                    blacklistWordUseCase.execute(event.wordId, false)
                            .map { blacklistWordResult ->
                                blacklistWordResult.fold({
                                    BlacklistUpdate.FailedToAllowWord(it, event.wordId, event.word)
                                }, {
                                    BlacklistUpdate.AllowWord(event.wordId, event.word)
                                })
                            }.toObservable().flatMap {
                                Observable.concat(
                                        Observable.just(it),
                                        Observable.timer(SNACKBAR_TIMEOUT_SECONDS, TimeUnit.SECONDS).map { BlacklistUpdate.SnackbarTimedOut(event.wordId) }
                                )
                            }
                }
                is BlacklistEvent.OnUndoAllow -> {
                    blacklistWordUseCase.execute(event.wordId, true).toObservable().flatMap { Observable.empty<BlacklistUpdate>() }
                }
                is BlacklistEvent.OnRemoveEvent -> {
                    softDeleteWordUseCase.execute(event.wordId, true).map { removeWordResult ->
                        removeWordResult.fold({
                            BlacklistUpdate.FailedToRemoveWord(it, event.wordId, event.word)
                        }, {
                            BlacklistUpdate.WordRemoved(event.wordId, event.word)
                        })
                    }.toObservable().flatMap {
                        Observable.concat(
                                Observable.just(it),
                                Observable.timer(SNACKBAR_TIMEOUT_SECONDS, TimeUnit.SECONDS).map { BlacklistUpdate.SnackbarTimedOut(event.wordId) }
                        )
                    }
                }
                is BlacklistEvent.OnUndoRemove -> {
                    softDeleteWordUseCase.execute(event.wordId, false).toObservable().flatMap { Observable.empty<BlacklistUpdate>() }
                }
            }
        }

    }

}

sealed class BlacklistUpdate {
    data class Blacklist(val words: List<DictionaryWord>) : BlacklistUpdate()
    data class WordRemoved(val wordId: Long, val word: String) : BlacklistUpdate()
    data class FailedToRemoveWord(val removeWordException: RemoveWordException, val wordId: Long, val word: String) : BlacklistUpdate()
    data class AllowWord(val wordId: Long, val word: String) : BlacklistUpdate()
    data class FailedToAllowWord(val blacklistWordException: BlacklistWordException, val wordId: Long, val word: String) : BlacklistUpdate()
    data class SnackbarTimedOut(val wordId: Long) : BlacklistUpdate()
}
