package com.android.inputmethod.ui.personaldictionary.upload

sealed class UploadUpdate {
    object UploadStarted : UploadUpdate()
    object UploadComplete : UploadUpdate()
    data class UploadFailed(val errorMessage: String) : UploadUpdate()
}