package com.android.inputmethod.ui.components.recycleradapter

import io.reactivex.Observable

interface ItemEventEmitter<ItemEvent> {
    fun events(): Observable<ItemEvent>
}