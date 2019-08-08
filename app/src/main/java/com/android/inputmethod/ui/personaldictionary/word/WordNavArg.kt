package com.android.inputmethod.ui.personaldictionary.word

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class WordNavArg(
        val wordId: Long,
        val word: String
) : Parcelable