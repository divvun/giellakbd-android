package com.android.inputmethod.ui.personaldictionary.dictionary

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class DictionaryNavArg(
        val languageId: Long
) : Parcelable