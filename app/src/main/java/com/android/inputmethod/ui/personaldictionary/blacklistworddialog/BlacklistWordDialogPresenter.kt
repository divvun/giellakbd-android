package com.android.inputmethod.ui.personaldictionary.blacklistworddialog

import com.android.inputmethod.usecases.AddBlacklistWordException
import com.android.inputmethod.usecases.BlacklistWordUseCase
import com.android.inputmethod.usecases.ValidateWordUseCase
import com.android.inputmethod.usecases.ValidationException
import io.reactivex.Observable

class BlacklistWordDialogPresenter(
        private val view: BlacklistWordDialogView,
        private val blacklistWordUseCase: BlacklistWordUseCase,
        private val validateWordUseCase: ValidateWordUseCase
) {
    private val initialViewState: BlacklistWordDialogViewState = BlacklistWordDialogViewState()


    fun start(): Observable<BlacklistWordDialogViewState> {
        return view.events().flatMap { event ->
            when (event) {
                is BlacklistWordDialogEvent.OnDialogInput -> {
                    validateWordUseCase.execute(event.word.trim()).map { result ->
                        result.fold({
                            BlacklistWordUpdate.ValidationError(it)
                        }, {
                            BlacklistWordUpdate.ValidationSuccess
                        })
                    }.toObservable()
                }
                is BlacklistWordDialogEvent.OnDialogBlacklistWordEvent -> {
                    blacklistWordUseCase.execute(view.languageId, event.word.trim()).map { result ->
                        result.fold({
                            BlacklistWordUpdate.BlacklistWordError(it)
                        }, {
                            BlacklistWordUpdate.BlacklistWordSuccess
                        })
                    }.toObservable()
                }
            }
        }.scan(initialViewState, { state, event ->
            when (event) {
                BlacklistWordUpdate.ValidationSuccess -> {
                    state.copy(error = null, blacklistWordEnabled = true)
                }
                is BlacklistWordUpdate.ValidationError -> {
                    state.copy(error = event.validationException.toBlacklistWordError(), blacklistWordEnabled = false)
                }
                BlacklistWordUpdate.BlacklistWordSuccess -> {
                    view.dismiss()
                    state
                }
                is BlacklistWordUpdate.BlacklistWordError -> {
                    state.copy(error = event.blacklistWordException.toBlacklistWordError(), blacklistWordEnabled = false)
                }
            }
        })
    }
}

sealed class BlacklistWordUpdate {
    object ValidationSuccess : BlacklistWordUpdate()
    data class ValidationError(val validationException: ValidationException) : BlacklistWordUpdate()
    object BlacklistWordSuccess : BlacklistWordUpdate()
    data class BlacklistWordError(val blacklistWordException: AddBlacklistWordException) : BlacklistWordUpdate()
}
