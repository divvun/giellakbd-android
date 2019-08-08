package com.android.inputmethod.ui.personaldictionary.upload

import com.android.inputmethod.usecases.UploadUseCase
import io.reactivex.Observable

class UploadPresenter(private val view: UploadView, private val useCase: UploadUseCase) {

    private val initialViewState = UploadViewState(
            uploadEnabled = true,
            loading = false,
            errorMessage = null
    )

    fun start(): Observable<UploadViewState> {
        return view.events().flatMap { event ->
            when (event) {
                is UploadEvent.OnUploadPressed -> {
                    Observable.concat(
                            Observable.just(UploadUpdate.UploadStarted),
                            useCase.execute().toObservable().map {
                                UploadUpdate.UploadComplete as UploadUpdate
                            }.onErrorReturn {
                                UploadUpdate.UploadFailed(it.message ?: "")
                            }
                    )
                }
            }
        }.scan(initialViewState, { viewState: UploadViewState, update: UploadUpdate ->
            when (update) {
                is UploadUpdate.UploadStarted -> {
                    viewState.copy(uploadEnabled = false, loading = true)
                }
                UploadUpdate.UploadComplete -> {
                    view.navigateToSuccess()
                    viewState
                }
                is UploadUpdate.UploadFailed -> {
                    viewState.copy(uploadEnabled = true, errorMessage = update.errorMessage, loading = false)
                }
            }
        })
    }

}