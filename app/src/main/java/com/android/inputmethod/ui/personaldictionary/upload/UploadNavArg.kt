package com.android.inputmethod.ui.personaldictionary.upload

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class UploadNavArg(
        val languageId: Long
) : Parcelable