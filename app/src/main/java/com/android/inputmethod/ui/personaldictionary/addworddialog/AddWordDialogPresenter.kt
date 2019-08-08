package com.android.inputmethod.ui.personaldictionary.addworddialog

import com.android.inputmethod.usecases.AddWordException
import com.android.inputmethod.usecases.AddWordUseCase
import com.android.inputmethod.usecases.ValidateWordUseCase
import com.android.inputmethod.usecases.ValidationException
import io.reactivex.Observable

class AddWordDialogPresenter(
        private val view: AddWordDialogView,
        private val addWordUseCase: AddWordUseCase,
        private val validateWordUseCase: ValidateWordUseCase
) {
    private val initialViewState: AddWordDialogViewState = AddWordDialogViewState()


    fun start(): Observable<AddWordDialogViewState> {
        return view.events().flatMap { event ->
            when (event) {
                is AddWordDialogEvent.OnDialogInput -> {
                    validateWordUseCase.execute(event.word.trim()).map { result ->
                        result.fold({
                            AddWordUpdate.ValidationError(it)
                        }, {
                            AddWordUpdate.ValidationSuccess
                        })
                    }.toObservable()
                }
                is AddWordDialogEvent.OnDialogAddWordEvent -> {
                    addWordUseCase.execute(view.languageId, event.word.trim()).map { result ->
                        result.fold({
                            AddWordUpdate.AddWordError(it)
                        }, {
                            AddWordUpdate.AddWordSuccess
                        })
                    }.toObservable()
                }
            }
        }.scan(initialViewState, { state, event ->
            when (event) {
                AddWordUpdate.ValidationSuccess -> {
                    state.copy(error = null, addWordEnabled = true)
                }
                is AddWordUpdate.ValidationError -> {
                    state.copy(error = event.validationException.toAddWordError(), addWordEnabled = false)
                }
                AddWordUpdate.AddWordSuccess -> {
                    view.dismiss()
                    state
                }
                is AddWordUpdate.AddWordError -> {
                    state.copy(error = event.addWordException.toAddWordError(), addWordEnabled = false)
                }
            }
        })
    }
}

sealed class AddWordUpdate {
    object ValidationSuccess : AddWordUpdate()
    data class ValidationError(val validationException: ValidationException) : AddWordUpdate()
    object AddWordSuccess : AddWordUpdate()
    data class AddWordError(val addWordException: AddWordException) : AddWordUpdate()
}
