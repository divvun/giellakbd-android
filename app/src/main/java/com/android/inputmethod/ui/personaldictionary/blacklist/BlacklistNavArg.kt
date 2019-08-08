package com.android.inputmethod.ui.personaldictionary.blacklist

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class BlacklistNavArg(
        val languageId: Long
) : Parcelable