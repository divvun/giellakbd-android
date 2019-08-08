package com.android.inputmethod.ui.personaldictionary.addworddialog

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class AddWordDialogNavArg(
        val languageId: Long
) : Parcelable