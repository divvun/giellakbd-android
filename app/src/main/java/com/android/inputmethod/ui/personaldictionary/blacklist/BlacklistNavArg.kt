package com.android.inputmethod.ui.personaldictionary.blacklist

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class BlacklistNavArg(
        val languageId: Long
) : Parcelable