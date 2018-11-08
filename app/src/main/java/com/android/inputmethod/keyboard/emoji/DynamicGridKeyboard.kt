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

package com.android.inputmethod.keyboard.emoji

import android.content.SharedPreferences
import android.text.TextUtils
import android.util.Log

import com.android.inputmethod.keyboard.Key
import com.android.inputmethod.keyboard.Keyboard
import com.android.inputmethod.latin.settings.Settings
import com.android.inputmethod.latin.utils.JsonUtils

import java.util.ArrayDeque
import java.util.ArrayList
import java.util.Collections

/**
 * This is a Keyboard class where you can add keys dynamically shown in a grid layout
 */
internal class DynamicGridKeyboard(private val mPrefs: SharedPreferences, templateKeyboard: Keyboard,
                                   private val mMaxKeyCount: Int, categoryId: Int) : Keyboard(templateKeyboard) {
    private val mLock = Any()
    private val mHorizontalStep: Int
    private val mVerticalStep: Int
    private val mColumnsNum: Int
    private val mIsRecents: Boolean
    private val mGridKeys = ArrayDeque<GridKey>()
    private val mPendingKeys = ArrayDeque<Key>()

    private var mCachedGridKeys: List<Key>? = null

    init {
        val key0 = getTemplateKey(TEMPLATE_KEY_CODE_0)
        val key1 = getTemplateKey(TEMPLATE_KEY_CODE_1)
        mHorizontalStep = Math.abs(key1.x - key0.x)
        mVerticalStep = key0.height + mVerticalGap
        mColumnsNum = mBaseWidth / mHorizontalStep
        mIsRecents = categoryId == EmojiCategory.ID_RECENTS
    }

    private fun getTemplateKey(code: Int): Key {
        for (key in super.getSortedKeys()) {
            if (key.code == code) {
                return key
            }
        }
        throw RuntimeException("Can't find template key: code=$code")
    }

    fun addPendingKey(usedKey: Key) {
        synchronized(mLock) {
            mPendingKeys.addLast(usedKey)
        }
    }

    fun flushPendingRecentKeys() {
        synchronized(mLock) {
            while (!mPendingKeys.isEmpty()) {
                addKey(mPendingKeys.pollFirst(), true)
            }
            saveRecentKeys()
        }
    }

    fun addKeyFirst(usedKey: Key) {
        addKey(usedKey, true)
        if (mIsRecents) {
            saveRecentKeys()
        }
    }

    fun addKeyLast(usedKey: Key?) {
        addKey(usedKey, false)
    }

    private fun addKey(usedKey: Key?, addFirst: Boolean) {
        if (usedKey == null) {
            return
        }
        synchronized(mLock) {
            mCachedGridKeys = null
            val key = GridKey(usedKey)
            while (mGridKeys.remove(key)) {
                // Remove duplicate keys.
            }
            if (addFirst) {
                mGridKeys.addFirst(key)
            } else {
                mGridKeys.addLast(key)
            }
            while (mGridKeys.size > mMaxKeyCount) {
                mGridKeys.removeLast()
            }
            var index = 0
            for (gridKey in mGridKeys) {
                val keyX0 = getKeyX0(index)
                val keyY0 = getKeyY0(index)
                val keyX1 = getKeyX1(index)
                val keyY1 = getKeyY1(index)
                gridKey.updateCoordinates(keyX0, keyY0, keyX1, keyY1)
                index++
            }
        }
    }

    private fun saveRecentKeys() {
        val keys = ArrayList<Any>()
        for (key in mGridKeys) {
            val outputText = key.outputText
            if (outputText != null) {
                keys.add(outputText)
            } else {
                keys.add(key.code)
            }
        }
        val jsonStr = JsonUtils.listToJsonStr(keys)
        Settings.writeEmojiRecentKeys(mPrefs, jsonStr)
    }

    fun loadRecentKeys(keyboards: Collection<DynamicGridKeyboard>) {
        val str = Settings.readEmojiRecentKeys(mPrefs)
        val keys = JsonUtils.jsonStrToList(str)
        for (o in keys) {
            val key: Key?
            if (o is Int) {
                key = getKeyByCode(keyboards, o)
            } else if (o is String) {
                key = getKeyByOutputText(keyboards, o)
            } else {
                Log.w(TAG, "Invalid object: $o")
                continue
            }
            addKeyLast(key)
        }
    }

    private fun getKeyX0(index: Int): Int {
        val column = index % mColumnsNum
        return column * mHorizontalStep
    }

    private fun getKeyX1(index: Int): Int {
        val column = index % mColumnsNum + 1
        return column * mHorizontalStep
    }

    private fun getKeyY0(index: Int): Int {
        val row = index / mColumnsNum
        return row * mVerticalStep + mVerticalGap / 2
    }

    private fun getKeyY1(index: Int): Int {
        val row = index / mColumnsNum + 1
        return row * mVerticalStep + mVerticalGap / 2
    }

    override fun getSortedKeys(): List<Key> {
        synchronized(mLock) {
            val gridKeys = mCachedGridKeys
            if (gridKeys != null) {
                return gridKeys
            }
            val cachedKeys = ArrayList<Key>(mGridKeys)
            val x = Collections.unmodifiableList(cachedKeys)
            mCachedGridKeys = x
            return x
        }
    }

    override fun getNearestKeys(x: Int, y: Int): List<Key> {
        // TODO: Calculate the nearest key index in mGridKeys from x and y.
        return sortedKeys
    }

    internal class GridKey(originalKey: Key) : Key(originalKey) {
        fun updateCoordinates(x0: Int, y0: Int, x1: Int, y1: Int) {
            this.mX = x0
//            this.x = x0
            this.mY = y0
            hitBox.set(x0, y0, x1, y1)
        }

        override fun equals(o: Any?): Boolean {
            if (o !is Key) return false
            val key = o as Key?
            if (code != key!!.code) return false
            return if (!TextUtils.equals(label, key.label)) false else TextUtils.equals(outputText, key.outputText)
        }

        override fun toString(): String {
            return "GridKey: " + super.toString()
        }
    }

    companion object {
        private val TAG = DynamicGridKeyboard::class.java.simpleName
        private val TEMPLATE_KEY_CODE_0 = 0x30
        private val TEMPLATE_KEY_CODE_1 = 0x31

        private fun getKeyByCode(keyboards: Collection<DynamicGridKeyboard>,
                                 code: Int): Key? {
            for (keyboard in keyboards) {
                for (key in keyboard.sortedKeys) {
                    if (key.code == code) {
                        return key
                    }
                }
            }
            return null
        }

        private fun getKeyByOutputText(keyboards: Collection<DynamicGridKeyboard>,
                                       outputText: String): Key? {
            for (keyboard in keyboards) {
                for (key in keyboard.sortedKeys) {
                    if (outputText == key.outputText) {
                        return key
                    }
                }
            }
            return null
        }
    }
}
