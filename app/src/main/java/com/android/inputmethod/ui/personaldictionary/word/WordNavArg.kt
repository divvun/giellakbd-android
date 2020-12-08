package com.android.inputmethod.ui.personaldictionary.word

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class WordNavArg(
        val wordId: Long,
        val word: String
) : Parcelable