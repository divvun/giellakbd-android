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

import java.util.ArrayList

/**
 * A combiner that handles dead keys.
 */
class SoftDeadKeyCombiner : Combiner {

    private val deadKeyRoot: Node.Parent = Node.Parent(
            'a' to Node.Parent(
                    'a' to Node.Leaf('â'),
                    'b' to Node.Parent(
                            'a' to Node.Leaf('á'),
                            'b' to Node.Leaf('à'),
                            ' ' to Node.Leaf('ã')
                    ),
                    ' ' to Node.Leaf('a')
            )
    )

    init {
        deadKeyRoot.parent = deadKeyRoot
    }

    private var currentNode: Node.Parent = deadKeyRoot

    private val deadSequence: MutableList<Event> = mutableListOf()

    lateinit var firstEvent: Event

    override fun processEvent(previousEvents: ArrayList<Event>, event: Event): Event {
        if(event.isHardwareEvent) {
            return event
        }

        if (deadSequence.isEmpty()) {
            // No dead char is currently being tracked: this is the most common case.
            if (event.isDead) {
                deadSequence.add(event)
                // The event was a dead key. Start tracking it.
                firstEvent = event

                currentNode = deadKeyRoot.map[event.codePointChar()] as Node.Parent
                return Event.createConsumedEvent(event)
            }
            // Regular keystroke when not keeping track of a dead key. Simply said, there are
            // no dead keys at all in the current input, so this combiner has nothing to do and
            // simply returns the event as is. The majority of events will go through this path.
            return event
        }

        if (event.isFunctionalKeyEvent) {
            if (Constants.CODE_DELETE == event.mKeyCode) {
                // Node didn't exist use fallback node
                val result = currentNode.map[' '] as Node.Leaf
                deadSequence.clear()
                currentNode = deadKeyRoot
                return Event.createSoftDeadResultEvent(result.char.toInt(), event)
            }
            return event
        }

        /**
        if (event.isDead) {
        mDeadSequence.appendCodePoint(event.mCodePoint)
        return Event.createConsumedEvent(event)
        }
         */

        // Combine normally.
        val inputValue = event.codePointChar()

        deadSequence.add(event)
        val newNode = currentNode.map[inputValue]
        if (newNode != null) {
            return when (newNode) {
                is Node.Parent -> {
                    currentNode = newNode
                    Event.createConsumedEvent(event)
                }
                is Node.Leaf -> {
                    val result = newNode.char
                    deadSequence.clear()
                    currentNode = deadKeyRoot
                    Event.createSoftDeadResultEvent(result.toInt(), event)
                }
            }
        } else {
            // Node didn't exist use fallback node
            val result = currentNode.map[' '] as Node.Leaf
            deadSequence.clear()
            currentNode = deadKeyRoot
            Log.d("DeadkeyCombiner", "Event Unknown char: $inputValue using default result: ${result.char}")
            return Event.createSoftDeadResultEvent(result.char.toInt(), event)
        }
    }

    override fun reset() {
        deadSequence.clear()
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

    private fun Event.codePointChar(): Char {
        return StringBuilder().appendCodePoint(this.mCodePoint).toString()[0]
    }

    sealed class Node {
        data class Leaf(val char: Char) : Node()
        data class Parent(val map: Map<Char, Node>) : Node() {
            constructor(vararg pairs: Pair<Char, Node>) : this(mapOf(*pairs))

            init {
                map.values.forEach { child ->
                    child.parent = this
                }
            }
        }

        lateinit var parent: Parent
    }


}
