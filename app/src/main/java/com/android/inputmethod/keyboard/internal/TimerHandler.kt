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
package com.android.inputmethod.keyboard.internal

import android.os.Message
import android.os.SystemClock
import com.android.inputmethod.keyboard.internal.DrawingProxy
import com.android.inputmethod.latin.utils.LeakGuardHandlerWrapper
import com.android.inputmethod.keyboard.internal.TimerProxy
import com.android.inputmethod.keyboard.internal.TimerHandler
import com.android.inputmethod.keyboard.PointerTracker
import android.view.ViewConfiguration
import com.android.inputmethod.keyboard.Key
import com.android.inputmethod.latin.common.Constants

class TimerHandler(ownerInstance: DrawingProxy,
                   private val mIgnoreAltCodeKeyTimeout: Int, private val mGestureRecognitionUpdateTime: Int) : LeakGuardHandlerWrapper<DrawingProxy>(ownerInstance), TimerProxy {
    override fun handleMessage(msg: Message) {
        val drawingProxy = ownerInstance ?: return
        when (msg.what) {
            MSG_TYPING_STATE_EXPIRED -> drawingProxy.startWhileTypingAnimation(DrawingProxy.FADE_IN)
            MSG_REPEAT_KEY -> {
                val tracker1 = msg.obj as PointerTracker
                tracker1.onKeyRepeat(msg.arg1 /* code */, msg.arg2 /* repeatCount */)
            }
            MSG_LONGPRESS_KEY, MSG_LONGPRESS_SHIFT_KEY -> {
                cancelLongPressTimers()
                val tracker2 = msg.obj as PointerTracker
                tracker2.onLongPressed()
            }
            MSG_UPDATE_BATCH_INPUT -> {
                val tracker3 = msg.obj as PointerTracker
                tracker3.updateBatchInputByTimer(SystemClock.uptimeMillis())
                startUpdateBatchInputTimer(tracker3)
            }
            MSG_DISMISS_KEY_PREVIEW -> drawingProxy.onKeyReleased((msg.obj as Key), false /* withAnimation */)
            MSG_DISMISS_GESTURE_FLOATING_PREVIEW_TEXT -> drawingProxy.dismissGestureFloatingPreviewTextWithoutDelay()
        }
    }

    override fun startKeyRepeatTimerOf(tracker: PointerTracker, repeatCount: Int,
                                       delay: Int) {
        val key = tracker.key
        if (key == null || delay == 0) {
            return
        }
        sendMessageDelayed(
                obtainMessage(MSG_REPEAT_KEY, key.code, repeatCount, tracker), delay.toLong())
    }

    private fun cancelKeyRepeatTimerOf(tracker: PointerTracker) {
        removeMessages(MSG_REPEAT_KEY, tracker)
    }

    fun cancelKeyRepeatTimers() {
        removeMessages(MSG_REPEAT_KEY)
    }

    // TODO: Suppress layout changes in key repeat mode
    val isInKeyRepeat: Boolean
        get() = hasMessages(MSG_REPEAT_KEY)

    override fun startLongPressTimerOf(tracker: PointerTracker, delay: Int) {
        val key = tracker.key ?: return
        // Use a separate message id for long pressing shift key, because long press shift key
        // timers should be canceled when other key is pressed.
        val messageId = if (key.code == Constants.CODE_SHIFT) MSG_LONGPRESS_SHIFT_KEY else MSG_LONGPRESS_KEY
        sendMessageDelayed(obtainMessage(messageId, tracker), delay.toLong())
    }

    override fun cancelLongPressTimersOf(tracker: PointerTracker) {
        removeMessages(MSG_LONGPRESS_KEY, tracker)
        removeMessages(MSG_LONGPRESS_SHIFT_KEY, tracker)
    }

    override fun cancelLongPressShiftKeyTimer() {
        removeMessages(MSG_LONGPRESS_SHIFT_KEY)
    }

    fun cancelLongPressTimers() {
        removeMessages(MSG_LONGPRESS_KEY)
        removeMessages(MSG_LONGPRESS_SHIFT_KEY)
    }

    override fun startTypingStateTimer(typedKey: Key) {
        if (typedKey.isModifier || typedKey.altCodeWhileTyping()) {
            return
        }
        val isTyping = isTypingState
        removeMessages(MSG_TYPING_STATE_EXPIRED)
        val drawingProxy = ownerInstance ?: return

        // When user hits the space or the enter key, just cancel the while-typing timer.
        val typedCode = typedKey.code
        if (typedCode == Constants.CODE_SPACE || typedCode == Constants.CODE_ENTER) {
            if (isTyping) {
                drawingProxy.startWhileTypingAnimation(DrawingProxy.FADE_IN)
            }
            return
        }
        sendMessageDelayed(
                obtainMessage(MSG_TYPING_STATE_EXPIRED), mIgnoreAltCodeKeyTimeout.toLong())
        if (isTyping) {
            return
        }
        drawingProxy.startWhileTypingAnimation(DrawingProxy.FADE_OUT)
    }

    override fun isTypingState(): Boolean {
        return hasMessages(MSG_TYPING_STATE_EXPIRED)
    }

    override fun startDoubleTapShiftKeyTimer() {
        sendMessageDelayed(obtainMessage(MSG_DOUBLE_TAP_SHIFT_KEY),
                ViewConfiguration.getDoubleTapTimeout().toLong())
    }

    override fun cancelDoubleTapShiftKeyTimer() {
        removeMessages(MSG_DOUBLE_TAP_SHIFT_KEY)
    }

    override fun isInDoubleTapShiftKeyTimeout(): Boolean {
        return hasMessages(MSG_DOUBLE_TAP_SHIFT_KEY)
    }

    override fun cancelKeyTimersOf(tracker: PointerTracker) {
        cancelKeyRepeatTimerOf(tracker)
        cancelLongPressTimersOf(tracker)
    }

    fun cancelAllKeyTimers() {
        cancelKeyRepeatTimers()
        cancelLongPressTimers()
    }

    override fun startUpdateBatchInputTimer(tracker: PointerTracker) {
        if (mGestureRecognitionUpdateTime <= 0) {
            return
        }
        removeMessages(MSG_UPDATE_BATCH_INPUT, tracker)
        sendMessageDelayed(obtainMessage(MSG_UPDATE_BATCH_INPUT, tracker),
                mGestureRecognitionUpdateTime.toLong())
    }

    override fun cancelUpdateBatchInputTimer(tracker: PointerTracker) {
        removeMessages(MSG_UPDATE_BATCH_INPUT, tracker)
    }

    override fun cancelAllUpdateBatchInputTimers() {
        removeMessages(MSG_UPDATE_BATCH_INPUT)
    }

    fun postDismissKeyPreview(key: Key, delay: Long) {
        sendMessageDelayed(obtainMessage(MSG_DISMISS_KEY_PREVIEW, key), delay)
    }

    fun postDismissGestureFloatingPreviewText(delay: Long) {
        sendMessageDelayed(obtainMessage(MSG_DISMISS_GESTURE_FLOATING_PREVIEW_TEXT), delay)
    }

    fun cancelAllMessages() {
        cancelAllKeyTimers()
        cancelAllUpdateBatchInputTimers()
        removeMessages(MSG_DISMISS_KEY_PREVIEW)
        removeMessages(MSG_DISMISS_GESTURE_FLOATING_PREVIEW_TEXT)
    }

    companion object {
        private const val MSG_TYPING_STATE_EXPIRED = 0
        private const val MSG_REPEAT_KEY = 1
        private const val MSG_LONGPRESS_KEY = 2
        private const val MSG_LONGPRESS_SHIFT_KEY = 3
        private const val MSG_DOUBLE_TAP_SHIFT_KEY = 4
        private const val MSG_UPDATE_BATCH_INPUT = 5
        private const val MSG_DISMISS_KEY_PREVIEW = 6
        private const val MSG_DISMISS_GESTURE_FLOATING_PREVIEW_TEXT = 7
        private const val MSG_CHANGE_LAYER = 8
    }
}