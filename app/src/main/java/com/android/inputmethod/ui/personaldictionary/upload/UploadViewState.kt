package com.android.inputmethod.ui.personaldictionary.upload

data class UploadViewState(
        val uploadEnabled: Boolean = true,
        val loading: Boolean = false,
        val errorMessage: String? = null
)