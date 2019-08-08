package com.android.inputmethod.ui.personaldictionary.upload

sealed class UploadEvent {
    object OnUploadPressed: UploadEvent()
}