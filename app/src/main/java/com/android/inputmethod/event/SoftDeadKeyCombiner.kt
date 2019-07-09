/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.inputmethod.event

import android.util.Log
import com.android.inputmethod.latin.common.Constants
import no.divvun.domain.DeadKeyNode
import java.util.*

/**
 * A combiner that handles dead keys.
 */
class SoftDeadKeyCombiner(private val deadKeyRoot: DeadKeyNode.Parent) : Combiner {

    private var currentNode: DeadKeyNode.Parent = deadKeyRoot
    private val deadSequence: MutableList<Event> = mutableListOf()
    lateinit var firstEvent: Event

    override fun processEvent(previousEvents: ArrayList<Event>, event: Event): Event {
        if (event.isHardwareEvent) {
            return event
        }

        if (deadSequence.isEmpty()) {
            // No dead char is currently being tracked: this is the most common case.
            if (event.isDead) {
                deadSequence.add(event)
                // The event was a dead key. Start tracking it.
                firstEvent = event


                val firstNode = deadKeyRoot.children[event.codePointChar()]
                if (firstNode == null) {
                    Log.e("SoftDeadKeyCombiner", "Key code ${event.mKeyCode}, Code Point ${event.mCodePoint} reported deadkey but was not supported")
                    return event
                }
                currentNode = firstNode as DeadKeyNode.Parent

                return Event.createConsumedEvent(event)
            }
            // Regular keystroke when not keeping track of a dead key. Simply said, there are
            // no dead keys at all in the current input, so this combiner has nothing to do and
            // simply returns the event as is. The majority of events will go through this path.
            return event
        }

        if (event.isFunctionalKeyEvent) {
            if (Constants.CODE_DELETE == event.mKeyCode) {
                // DeadKeyDeadKeyNode didn't exist use fallback node
                val result = currentNode.defaultChild()
                reset()
                return Event.createSoftDeadResultEvent(result.string, event)
            }
            return event
        }

        // Combine normally.
        val inputValue = event.codePointChar()

        deadSequence.add(event)
        val newDeadKeyNode = currentNode.children[inputValue]
        if (newDeadKeyNode != null) {
            return when (newDeadKeyNode) {
                is DeadKeyNode.Parent -> {
                    currentNode = newDeadKeyNode
                    Event.createConsumedEvent(event)
                }
                is DeadKeyNode.Leaf -> {
                    val result = newDeadKeyNode.string
                    reset()
                    Event.createSoftDeadResultEvent(result, event)
                }
            }
        } else {
            // DeadKeyDeadKeyNode didn't exist use fallback node
            val result = currentNode.defaultChild()
            reset()
            return Event.createSoftDeadResultEvent(result.string, event)
        }
    }

    override fun reset() {
        deadSequence.clear()
        currentNode = deadKeyRoot
    }

    override fun getCombiningStateFeedback(): CharSequence {
        val sb: StringBuilder = StringBuilder()

        deadSequence.map {
            it.mCodePoint
        }.forEach {
            sb.appendCodePoint(it)
        }

        return sb.toString()
    }

    private fun Event.codePointChar(): String {
        return StringBuilder().appendCodePoint(this.mCodePoint).toString()
    }
}
