/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.inputmethod.latin.suggestions

import android.content.Context
import android.content.res.Resources
import android.graphics.Paint
import android.graphics.drawable.Drawable

import com.android.inputmethod.keyboard.Key
import com.android.inputmethod.keyboard.Keyboard
import com.android.inputmethod.keyboard.internal.KeyboardBuilder
import com.android.inputmethod.keyboard.internal.KeyboardIconsSet
import com.android.inputmethod.keyboard.internal.KeyboardParams
import com.android.inputmethod.latin.R
import com.android.inputmethod.latin.SuggestedWords
import com.android.inputmethod.latin.common.Constants
import com.android.inputmethod.latin.utils.TypefaceUtils

class MoreSuggestions internal constructor(params: MoreSuggestionsParam, val mSuggestedWords: SuggestedWords) : Keyboard(params) {

    internal class MoreSuggestionsParam : KeyboardParams() {
        private val mWidths = IntArray(SuggestedWords.MAX_SUGGESTIONS)
        private val mRowNumbers = IntArray(SuggestedWords.MAX_SUGGESTIONS)
        private val mColumnOrders = IntArray(SuggestedWords.MAX_SUGGESTIONS)
        private val mNumColumnsInRow = IntArray(SuggestedWords.MAX_SUGGESTIONS)
        private var mNumRows: Int = 0
        lateinit var mDivider: Drawable
        var mDividerWidth: Int = 0

        fun layout(suggestedWords: SuggestedWords, fromIndex: Int,
                   maxWidth: Int, minWidth: Int, maxRow: Int, paint: Paint,
                   res: Resources): Int {
            clearKeys()
            mDivider = res.getDrawable(R.drawable.more_suggestions_divider)
            mDividerWidth = mDivider.intrinsicWidth
            val padding = res.getDimension(
                    R.dimen.config_more_suggestions_key_horizontal_padding)

            var row = 0
            var index = fromIndex
            var rowStartIndex = fromIndex
            val size = Math.min(suggestedWords.size(), SuggestedWords.MAX_SUGGESTIONS)
            while (index < size) {
                val word: String
                if (isIndexSubjectToAutoCorrection(suggestedWords, index)) {
                    // INDEX_OF_AUTO_CORRECTION and INDEX_OF_TYPED_WORD got swapped.
                    word = suggestedWords.getLabel(SuggestedWords.INDEX_OF_TYPED_WORD)
                } else {
                    word = suggestedWords.getLabel(index)
                }
                // TODO: Should take care of text x-scaling.
                mWidths[index] = (TypefaceUtils.getStringWidth(word, paint) + padding).toInt()
                val numColumn = index - rowStartIndex + 1
                val columnWidth = (maxWidth - mDividerWidth * (numColumn - 1)) / numColumn
                if (numColumn > MAX_COLUMNS_IN_ROW || !fitInWidth(rowStartIndex, index + 1, columnWidth)) {
                    if (row + 1 >= maxRow) {
                        break
                    }
                    mNumColumnsInRow[row] = index - rowStartIndex
                    rowStartIndex = index
                    row++
                }
                mColumnOrders[index] = index - rowStartIndex
                mRowNumbers[index] = row
                index++
            }
            mNumColumnsInRow[row] = index - rowStartIndex
            mNumRows = row + 1
            mOccupiedWidth = Math.max(
                    minWidth, calcurateMaxRowWidth(fromIndex, index))
            mBaseWidth = mOccupiedWidth
            mOccupiedHeight = mNumRows * mDefaultRowHeight + mVerticalGap
            mBaseHeight = mOccupiedHeight
            return index - fromIndex
        }

        private fun fitInWidth(startIndex: Int, endIndex: Int, width: Int): Boolean {
            for (index in startIndex until endIndex) {
                if (mWidths[index] > width)
                    return false
            }
            return true
        }

        private fun calcurateMaxRowWidth(startIndex: Int, endIndex: Int): Int {
            var maxRowWidth = 0
            var index = startIndex
            for (row in 0 until mNumRows) {
                val numColumnInRow = mNumColumnsInRow[row]
                var maxKeyWidth = 0
                while (index < endIndex && mRowNumbers[index] == row) {
                    maxKeyWidth = Math.max(maxKeyWidth, mWidths[index])
                    index++
                }
                maxRowWidth = Math.max(maxRowWidth,
                        maxKeyWidth * numColumnInRow + mDividerWidth * (numColumnInRow - 1))
            }
            return maxRowWidth
        }

        fun getNumColumnInRow(index: Int): Int {
            return mNumColumnsInRow[mRowNumbers[index]]
        }

        fun getColumnNumber(index: Int): Int {
            val columnOrder = mColumnOrders[index]
            val numColumn = getNumColumnInRow(index)
            return COLUMN_ORDER_TO_NUMBER[numColumn - 1][columnOrder]
        }

        fun getX(index: Int): Int {
            val columnNumber = getColumnNumber(index)
            return columnNumber * (getWidth(index) + mDividerWidth)
        }

        fun getY(index: Int): Int {
            val row = mRowNumbers[index]
            return (mNumRows - 1 - row) * mDefaultRowHeight + mTopPadding
        }

        fun getWidth(index: Int): Int {
            val numColumnInRow = getNumColumnInRow(index)
            return (mOccupiedWidth - mDividerWidth * (numColumnInRow - 1)) / numColumnInRow
        }

        fun markAsEdgeKey(key: Key, index: Int) {
            val row = mRowNumbers[index]
            if (row == 0)
                key.markAsBottomEdge(this)
            if (row == mNumRows - 1)
                key.markAsTopEdge(this)

            val numColumnInRow = mNumColumnsInRow[row]
            val column = getColumnNumber(index)
            if (column == 0)
                key.markAsLeftEdge(this)
            if (column == numColumnInRow - 1)
                key.markAsRightEdge(this)
        }

        companion object {
            private val MAX_COLUMNS_IN_ROW = 3

            private val COLUMN_ORDER_TO_NUMBER = arrayOf(intArrayOf(0), // center
                    intArrayOf(1, 0), // right-left
                    intArrayOf(1, 0, 2))// center-left-right
        }
    }

    internal class Builder(context: Context, private val mPaneView: MoreSuggestionsView) : KeyboardBuilder<MoreSuggestionsParam>(context, MoreSuggestionsParam()) {
        private var mSuggestedWords: SuggestedWords? = null
        private var mFromIndex: Int = 0
        private var mToIndex: Int = 0

        fun layout(suggestedWords: SuggestedWords, fromIndex: Int,
                   maxWidth: Int, minWidth: Int, maxRow: Int,
                   parentKeyboard: Keyboard): Builder {
            val xmlId = R.xml.kbd_suggestions_pane_template
            load(xmlId, parentKeyboard.mId)
            mParams.mTopPadding = parentKeyboard.mVerticalGap / 2
            mParams.mVerticalGap = mParams.mTopPadding
            mPaneView.updateKeyboardGeometry(mParams.mDefaultRowHeight)
            val count = mParams.layout(suggestedWords, fromIndex, maxWidth, minWidth, maxRow,
                    mPaneView.newLabelPaint(null /* key */), mResources)
            mFromIndex = fromIndex
            mToIndex = fromIndex + count
            mSuggestedWords = suggestedWords
            return this
        }

        override fun build(): MoreSuggestions {
            val params = mParams
            for (index in mFromIndex until mToIndex) {
                val x = params.getX(index)
                val y = params.getY(index)
                val width = params.getWidth(index)
                val word: String
                val info: String?
                if (isIndexSubjectToAutoCorrection(mSuggestedWords!!, index)) {
                    // INDEX_OF_AUTO_CORRECTION and INDEX_OF_TYPED_WORD got swapped.
                    word = mSuggestedWords!!.getLabel(SuggestedWords.INDEX_OF_TYPED_WORD)
                    info = mSuggestedWords!!.getDebugString(SuggestedWords.INDEX_OF_TYPED_WORD)
                } else {
                    word = mSuggestedWords!!.getLabel(index)
                    info = mSuggestedWords!!.getDebugString(index)
                }
                val key = MoreSuggestionKey(word, info, index, params)
                params.markAsEdgeKey(key, index)
                params.onAddKey(key)
                val columnNumber = params.getColumnNumber(index)
                val numColumnInRow = params.getNumColumnInRow(index)
                if (columnNumber < numColumnInRow - 1) {
                    val divider = Divider(params, params.mDivider, x + width, y,
                            params.mDividerWidth, params.mDefaultRowHeight)
                    params.onAddKey(divider)
                }
            }
            return MoreSuggestions(params, mSuggestedWords!!)
        }
    }

    internal class MoreSuggestionKey(word: String, info: String?, val mSuggestedWordIndex: Int,
                                     params: MoreSuggestionsParam)/* label *//* outputText *//* labelFlags */ : Key(word, KeyboardIconsSet.ICON_UNDEFINED, Constants.CODE_OUTPUT_TEXT, word, info, 0, Key.BACKGROUND_TYPE_NORMAL, params.getX(mSuggestedWordIndex), params.getY(mSuggestedWordIndex), params.getWidth(mSuggestedWordIndex), params.mDefaultRowHeight, params.mHorizontalGap, params.mVerticalGap)

    private class Divider(params: KeyboardParams, private val mIcon: Drawable, x: Int,
                          y: Int, width: Int, height: Int) : Key.Spacer(params, x, y, width, height) {

        override fun getIcon(iconSet: KeyboardIconsSet, alpha: Int): Drawable? {
            // KeyboardIconsSet and alpha are unused. Use the icon that has been passed to the
            // constructor.
            // TODO: Drawable itself should have an alpha value.
            mIcon.alpha = 128
            return mIcon
        }
    }

    companion object {

        internal fun isIndexSubjectToAutoCorrection(suggestedWords: SuggestedWords,
                                                    index: Int): Boolean {
            return suggestedWords.mWillAutoCorrect && index == SuggestedWords.INDEX_OF_AUTO_CORRECTION
        }
    }
}
