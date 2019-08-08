package com.android.inputmethod.ui.personaldictionary.blacklistworddialog

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class BlacklistWordDialogNavArg(
        val languageId: Long
) : Parcelable