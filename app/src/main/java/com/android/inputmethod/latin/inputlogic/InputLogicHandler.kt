/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.inputmethod.latin.inputlogic

import android.os.Handler
import android.os.HandlerThread
import android.os.Message

import com.android.inputmethod.compat.LooperCompatUtils
import com.android.inputmethod.latin.LatinIME
import com.android.inputmethod.latin.SuggestedWords
import com.android.inputmethod.latin.Suggest.OnGetSuggestedWordsCallback
import com.android.inputmethod.latin.common.InputPointers

/**
 * A helper to manage deferred tasks for the input logic.
 */
internal open class InputLogicHandler : Handler.Callback {
    val mNonUIThreadHandler: Handler?
    // TODO: remove this reference.
    val mLatinIME: LatinIME?
    val mInputLogic: InputLogic?
    private val mLock = Any()
    var isInBatchInput: Boolean = false
        private set // synchronized using {@link #mLock}.

    constructor() {
        mNonUIThreadHandler = null
        mLatinIME = null
        mInputLogic = null
    }

    constructor(latinIME: LatinIME, inputLogic: InputLogic) {
        val handlerThread = HandlerThread(
                InputLogicHandler::class.java.simpleName)
        handlerThread.start()
        mNonUIThreadHandler = Handler(handlerThread.looper, this)
        mLatinIME = latinIME
        mInputLogic = inputLogic
    }

    open fun reset() {
        mNonUIThreadHandler!!.removeCallbacksAndMessages(null)
    }

    // In unit tests, we create several instances of LatinIME, which results in several instances
    // of InputLogicHandler. To avoid these handlers lingering, we call this.
    fun destroy() {
        mNonUIThreadHandler!!.looper.quitSafely()
    }

    /**
     * Handle a message.
     * @see android.os.Handler.Callback.handleMessage
     */
    // Called on the Non-UI handler thread by the Handler code.
    override fun handleMessage(msg: Message): Boolean {
        when (msg.what) {
            MSG_GET_SUGGESTED_WORDS -> mLatinIME!!.getSuggestedWords(msg.arg1 /* inputStyle */,
                    msg.arg2 /* sequenceNumber */, msg.obj as OnGetSuggestedWordsCallback)
        }
        return true
    }

    // Called on the UI thread by InputLogic.
    open fun onStartBatchInput() {
        synchronized(mLock) {
            isInBatchInput = true
        }
    }

    /**
     * Fetch suggestions corresponding to an update of a batch input.
     * @param batchPointers the updated pointers, including the part that was passed last time.
     * @param sequenceNumber the sequence number associated with this batch input.
     * @param isTailBatchInput true if this is the end of a batch input, false if it's an update.
     */
    // This method can be called from any thread and will see to it that the correct threads
    // are used for parts that require it. This method will send a message to the Non-UI handler
    // thread to pull suggestions, and get the inlined callback to get called on the Non-UI
    // handler thread. If this is the end of a batch input, the callback will then proceed to
    // send a message to the UI handler in LatinIME so that showing suggestions can be done on
    // the UI thread.
    private fun updateBatchInput(batchPointers: InputPointers,
                                 sequenceNumber: Int, isTailBatchInput: Boolean) {
        synchronized(mLock) {
            if (!isInBatchInput) {
                // Batch input has ended or canceled while the message was being delivered.
                return
            }
            mInputLogic!!.wordComposer.setBatchInputPointers(batchPointers)
            val callback = object : OnGetSuggestedWordsCallback {
                override fun onGetSuggestedWords(suggestedWords: SuggestedWords) {
                    showGestureSuggestionsWithPreviewVisuals(suggestedWords, isTailBatchInput)
                }
            }
            getSuggestedWords(if (isTailBatchInput)
                SuggestedWords.INPUT_STYLE_TAIL_BATCH
            else
                SuggestedWords.INPUT_STYLE_UPDATE_BATCH, sequenceNumber, callback)
        }
    }

    fun showGestureSuggestionsWithPreviewVisuals(suggestedWordsForBatchInput: SuggestedWords,
                                                 isTailBatchInput: Boolean) {
        val suggestedWordsToShowSuggestions: SuggestedWords
        // We're now inside the callback. This always runs on the Non-UI thread,
        // no matter what thread updateBatchInput was originally called on.
        if (suggestedWordsForBatchInput.isEmpty) {
            // Use old suggestions if we don't have any new ones.
            // Previous suggestions are found in InputLogic#mSuggestedWords.
            // Since these are the most recent ones and we just recomputed
            // new ones to update them, then the previous ones are there.
            suggestedWordsToShowSuggestions = mInputLogic!!.mSuggestedWords
        } else {
            suggestedWordsToShowSuggestions = suggestedWordsForBatchInput
        }
        mLatinIME!!.mHandler.showGesturePreviewAndSuggestionStrip(suggestedWordsToShowSuggestions,
                isTailBatchInput /* dismissGestureFloatingPreviewText */)
        if (isTailBatchInput) {
            isInBatchInput = false
            // The following call schedules onEndBatchInputInternal
            // to be called on the UI thread.
            mLatinIME.mHandler.showTailBatchInputResult(suggestedWordsToShowSuggestions)
        }
    }

    /**
     * Update a batch input.
     *
     * This fetches suggestions and updates the suggestion strip and the floating text preview.
     *
     * @param batchPointers the updated batch pointers.
     * @param sequenceNumber the sequence number associated with this batch input.
     */
    // Called on the UI thread by InputLogic.
    open fun onUpdateBatchInput(batchPointers: InputPointers,
                                sequenceNumber: Int) {
        updateBatchInput(batchPointers, sequenceNumber, false /* isTailBatchInput */)
    }

    /**
     * Cancel a batch input.
     *
     * Note that as opposed to updateTailBatchInput, we do the UI side of this immediately on the
     * same thread, rather than get this to call a method in LatinIME. This is because
     * canceling a batch input does not necessitate the long operation of pulling suggestions.
     */
    // Called on the UI thread by InputLogic.
    open fun onCancelBatchInput() {
        synchronized(mLock) {
            isInBatchInput = false
        }
    }

    /**
     * Trigger an update for a tail batch input.
     *
     * A tail batch input is the last update for a gesture, the one that is triggered after the
     * user lifts their finger. This method schedules fetching suggestions on the non-UI thread,
     * then when the suggestions are computed it comes back on the UI thread to update the
     * suggestion strip, commit the first suggestion, and dismiss the floating text preview.
     *
     * @param batchPointers the updated batch pointers.
     * @param sequenceNumber the sequence number associated with this batch input.
     */
    // Called on the UI thread by InputLogic.
    open fun updateTailBatchInput(batchPointers: InputPointers,
                                  sequenceNumber: Int) {
        updateBatchInput(batchPointers, sequenceNumber, true /* isTailBatchInput */)
    }

    open fun getSuggestedWords(inputStyle: Int, sequenceNumber: Int,
                               callback: OnGetSuggestedWordsCallback) {
        mNonUIThreadHandler?.obtainMessage(
                MSG_GET_SUGGESTED_WORDS, inputStyle, sequenceNumber, callback)?.sendToTarget()
    }

    open fun getSuggestedWords(inputStyle: Int, sequenceNumber: Int,
                               callback: ((SuggestedWords) -> Unit)) {
        mNonUIThreadHandler?.obtainMessage(
                MSG_GET_SUGGESTED_WORDS, inputStyle, sequenceNumber, object: OnGetSuggestedWordsCallback {
            override fun onGetSuggestedWords(suggestedWords: SuggestedWords) {
                callback(suggestedWords)
            }

        })?.sendToTarget()
    }

    companion object {

        private val MSG_GET_SUGGESTED_WORDS = 1

        // A handler that never does anything. This is used for cases where events come before anything
        // is initialized, though probably only the monkey can actually do this.
        val NULL_HANDLER: InputLogicHandler = object : InputLogicHandler() {
            override fun reset() {}
            override fun handleMessage(msg: Message): Boolean {
                return true
            }

            override fun onStartBatchInput() {}
            override fun onUpdateBatchInput(batchPointers: InputPointers,
                                            sequenceNumber: Int) {
            }

            override fun onCancelBatchInput() {}
            override fun updateTailBatchInput(batchPointers: InputPointers,
                                              sequenceNumber: Int) {
            }

            override fun getSuggestedWords(sessionId: Int, sequenceNumber: Int,
                                           callback: OnGetSuggestedWordsCallback) {
            }
        }
    }
}
