package com.android.inputmethod.ui.components.recycleradapter

import timber.log.Timber

interface Diffable {

    fun isSameAs(other: Diffable): Boolean

    fun hasSameContentAs(other: Diffable): Boolean {
        return this == other
    }

    fun getDiff(`object`: Any): Any {
        Timber.d(TAG, "This: $this, Other: $`object`")
        return this
    }

    companion object {
        val TAG = Diffable::class.java.simpleName
    }
}