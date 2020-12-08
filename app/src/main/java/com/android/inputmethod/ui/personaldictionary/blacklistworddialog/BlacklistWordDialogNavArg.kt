package com.android.inputmethod.ui.personaldictionary.blacklistworddialog

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class BlacklistWordDialogNavArg(
        val languageId: Long
) : Parcelable