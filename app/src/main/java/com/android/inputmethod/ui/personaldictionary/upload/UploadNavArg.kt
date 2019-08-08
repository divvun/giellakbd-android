package com.android.inputmethod.ui.personaldictionary.upload

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class UploadNavArg(
        val languageId: Long
) : Parcelable