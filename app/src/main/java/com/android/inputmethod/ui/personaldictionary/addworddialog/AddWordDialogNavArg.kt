package com.android.inputmethod.ui.personaldictionary.addworddialog

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class AddWordDialogNavArg(
        val languageId: Long
) : Parcelable