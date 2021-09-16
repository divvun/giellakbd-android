/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.inputmethod.latin

import android.text.TextUtils
import android.view.inputmethod.CompletionInfo

import com.android.inputmethod.annotations.UsedForTesting
import com.android.inputmethod.latin.common.StringUtils
import com.android.inputmethod.latin.define.DebugFlags
import com.android.inputmethod.latin.utils.TypefaceUtils

import java.util.ArrayList
import java.util.Arrays
import java.util.HashSet

open class SuggestedWords(protected val mSuggestedWordInfoList: ArrayList<SuggestedWordInfo>,
                          val mRawSuggestions: ArrayList<SuggestedWordInfo>?,
                          /**
                           * Get [SuggestedWordInfo] object for the typed word.
                           * @return The [SuggestedWordInfo] object for the typed word.
                           */
                          val typedWordInfo: SuggestedWordInfo?,
                          val mTypedWordValid: Boolean,
        // Note: this INCLUDES cases where the word will auto-correct to itself. A good definition
        // of what this flag means would be "the top suggestion is strong enough to auto-correct",
        // whether this exactly matches the user entry or not.
                          val mWillAutoCorrect: Boolean,
                          val mIsObsoleteSuggestions: Boolean,
        // How the input for these suggested words was done by the user. Must be one of the
        // INPUT_STYLE_* constants above.
                          val mInputStyle: Int,
                          val mSequenceNumber: Int // Sequence number for auto-commit.
) {

    val isEmpty: Boolean
        get() = mSuggestedWordInfoList.isEmpty()

    /**
     * The predicator to tell whether this object represents punctuation suggestions.
     * @return false if this object desn't represent punctuation suggestions.
     */
    open val isPunctuationSuggestions: Boolean
        get() = false

    val autoCommitCandidate: SuggestedWordInfo?
        get() {
            if (mSuggestedWordInfoList.size <= 0) return null
            val candidate = mSuggestedWordInfoList[0]
            return if (candidate.isEligibleForAutoCommit) candidate else null
        }

    val isPrediction: Boolean
        get() = isPrediction(mInputStyle)

    /**
     * @return the [SuggestedWordInfo] which corresponds to the word that is originally
     * typed by the user. Otherwise returns `null`. Note that gesture input is not
     * considered to be a typed word.
     */
    val typedWordInfoOrNull: SuggestedWordInfo?
        @UsedForTesting
        get() {
            if (SuggestedWords.INDEX_OF_TYPED_WORD >= size()) {
                return null
            }
            val info = getInfo(SuggestedWords.INDEX_OF_TYPED_WORD)
            return if (info?.kind == SuggestedWordInfo.KIND_TYPED) info else null
        }

    fun size(): Int {
        return mSuggestedWordInfoList.size
    }

    /**
     * Get suggested word to show as suggestions to UI.
     *
     * @param shouldShowLxxSuggestionUi true if showing suggestion UI introduced in LXX and later.
     * @return the count of suggested word to show as suggestions to UI.
     */
    fun getWordCountToShow(shouldShowLxxSuggestionUi: Boolean): Int {
        return if (isPrediction || !shouldShowLxxSuggestionUi) {
            size()
        } else size() - /* typed word */ 1
    }

    /**
     * Get suggested word at `index`.
     * @param index The index of the suggested word.
     * @return The suggested word.
     */
    open fun getWord(index: Int): String {
        return mSuggestedWordInfoList[index].word
    }

    /**
     * Get displayed text at `index`.
     * In RTL languages, the displayed text on the suggestion strip may be different from the
     * suggested word that is returned from [.getWord]. For example the displayed text
     * of punctuation suggestion "(" should be ")".
     * @param index The index of the text to display.
     * @return The text to be displayed.
     */
    open fun getLabel(index: Int): String {
        return mSuggestedWordInfoList[index].word
    }

    /**
     * Get [SuggestedWordInfo] object at `index`.
     * @param index The index of the [SuggestedWordInfo].
     * @return The [SuggestedWordInfo] object.
     */
    open fun getInfo(index: Int): SuggestedWordInfo? {
        return mSuggestedWordInfoList[index]
    }

    /**
     * Gets the suggestion index from the suggestions list.
     * @param suggestedWordInfo The [SuggestedWordInfo] to find the index.
     * @return The position of the suggestion in the suggestion list.
     */
    fun indexOf(suggestedWordInfo: SuggestedWordInfo): Int {
        return mSuggestedWordInfoList.indexOf(suggestedWordInfo)
    }

    fun getDebugString(pos: Int): String? {
        if (!DebugFlags.DEBUG_ENABLED) {
            return null
        }
        val wordInfo = getInfo(pos) ?: return null
        val debugString = wordInfo.debugString
        return if (TextUtils.isEmpty(debugString)) {
            null
        } else debugString
    }

    override fun toString(): String {
        // Pretty-print method to help debug
        return ("SuggestedWords:"
                + " mTypedWordValid=" + mTypedWordValid
                + " mWillAutoCorrect=" + mWillAutoCorrect
                + " mInputStyle=" + mInputStyle
                + " words=" + Arrays.toString(mSuggestedWordInfoList.toTypedArray()))
    }

    // non-final for testability.
    class SuggestedWordInfo {

        val word: String
        val mPrevWordsContext: String
        // The completion info from the application. Null for suggestions that don't come from
        // the application (including keyboard-computed ones, so this is almost always null)
        val mApplicationSpecifiedCompletionInfo: CompletionInfo?
        val mScore: Int
        val mKindAndFlags: Int
        val mCodePointCount: Int
        @Deprecated("")
        @get:Deprecated("")
        val sourceDictionary: Dictionary
        // For auto-commit. This keeps track of the index inside the touch coordinates array
        // passed to native code to get suggestions for a gesture that corresponds to the first
        // letter of the second word.
        val mIndexOfTouchPointOfSecondWord: Int
        // For auto-commit. This is a measure of how confident we are that we can commit the
        // first word of this suggestion.
        val mAutoCommitFirstWordConfidence: Int
        var debugString: String? = ""
            set(str) {
                if (null == str) throw NullPointerException("Debug info is null")
                field = str
            }

        val isEligibleForAutoCommit: Boolean
            get() = isKindOf(KIND_CORRECTION) && NOT_AN_INDEX != mIndexOfTouchPointOfSecondWord

        val kind: Int
            get() = mKindAndFlags and KIND_MASK_KIND

        val isPossiblyOffensive: Boolean
            get() = mKindAndFlags and KIND_FLAG_POSSIBLY_OFFENSIVE != 0

        val isExactMatch: Boolean
            get() = mKindAndFlags and KIND_FLAG_EXACT_MATCH != 0

        val isExactMatchWithIntentionalOmission: Boolean
            get() = mKindAndFlags and KIND_FLAG_EXACT_MATCH_WITH_INTENTIONAL_OMISSION != 0

        val isAprapreateForAutoCorrection: Boolean
            get() = mKindAndFlags and KIND_FLAG_APPROPRIATE_FOR_AUTO_CORRECTION != 0

        /**
         * Create a new suggested word info.
         * @param word The string to suggest.
         * @param prevWordsContext previous words context.
         * @param score A measure of how likely this suggestion is.
         * @param kindAndFlags The kind of suggestion, as one of the above KIND_* constants with
         * flags.
         * @param sourceDict What instance of Dictionary produced this suggestion.
         * @param indexOfTouchPointOfSecondWord See mIndexOfTouchPointOfSecondWord.
         * @param autoCommitFirstWordConfidence See mAutoCommitFirstWordConfidence.
         */
        constructor(word: String, prevWordsContext: String,
                    score: Int, kindAndFlags: Int,
                    sourceDict: Dictionary, indexOfTouchPointOfSecondWord: Int,
                    autoCommitFirstWordConfidence: Int) {
            this.word = word
            mPrevWordsContext = prevWordsContext
            mApplicationSpecifiedCompletionInfo = null
            mScore = score
            mKindAndFlags = kindAndFlags
            sourceDictionary = sourceDict
            mCodePointCount = TypefaceUtils.getGraphemeClusterCount(this.word)
            mIndexOfTouchPointOfSecondWord = indexOfTouchPointOfSecondWord
            mAutoCommitFirstWordConfidence = autoCommitFirstWordConfidence
        }

        /**
         * Create a new suggested word info from an application-specified completion.
         * If the passed argument or its contained text is null, this throws a NPE.
         * @param applicationSpecifiedCompletion The application-specified completion info.
         */
        constructor(applicationSpecifiedCompletion: CompletionInfo) {
            word = applicationSpecifiedCompletion.text.toString()
            mPrevWordsContext = ""
            mApplicationSpecifiedCompletionInfo = applicationSpecifiedCompletion
            mScore = SuggestedWordInfo.MAX_SCORE
            mKindAndFlags = SuggestedWordInfo.KIND_APP_DEFINED
            sourceDictionary = Dictionary.DICTIONARY_APPLICATION_DEFINED
            mCodePointCount = TypefaceUtils.getGraphemeClusterCount(word)
            mIndexOfTouchPointOfSecondWord = SuggestedWordInfo.NOT_AN_INDEX
            mAutoCommitFirstWordConfidence = SuggestedWordInfo.NOT_A_CONFIDENCE
        }

        fun isKindOf(kind: Int): Boolean {
            return kind == kind
        }

        fun codePointAt(i: Int): Int {
            return word.codePointAt(i)
        }

        override fun toString(): String {
            return if (TextUtils.isEmpty(debugString)) {
                word
            } else "$word ($debugString)"
        }

        companion object {
            val NOT_AN_INDEX = -1
            val NOT_A_CONFIDENCE = -1
            val MAX_SCORE = Integer.MAX_VALUE

            private val KIND_MASK_KIND = 0xFF // Mask to get only the kind
            val KIND_TYPED = 0 // What user typed
            val KIND_CORRECTION = 1 // Simple correction/suggestion
            val KIND_COMPLETION = 2 // Completion (suggestion with appended chars)
            val KIND_WHITELIST = 3 // Whitelisted word
            val KIND_BLACKLIST = 4 // Blacklisted word
            val KIND_HARDCODED = 5 // Hardcoded suggestion, e.g. punctuation
            val KIND_APP_DEFINED = 6 // Suggested by the application
            val KIND_SHORTCUT = 7 // A shortcut
            val KIND_PREDICTION = 8 // A prediction (== a suggestion with no input)
            // KIND_RESUMED: A resumed suggestion (comes from a span, currently this type is used only
            // in java for re-correction)
            val KIND_RESUMED = 9
            val KIND_OOV_CORRECTION = 10 // Most probable string correction

            val KIND_FLAG_POSSIBLY_OFFENSIVE = -0x80000000
            val KIND_FLAG_EXACT_MATCH = 0x40000000
            val KIND_FLAG_EXACT_MATCH_WITH_INTENTIONAL_OMISSION = 0x20000000
            val KIND_FLAG_APPROPRIATE_FOR_AUTO_CORRECTION = 0x10000000

            /**
             * This will always remove the higher index if a duplicate is found.
             *
             * @return position of typed word in the candidate list
             */
            fun removeDups(
                    typedWord: String?,
                    candidates: ArrayList<SuggestedWordInfo>): Int {
                if (candidates.isEmpty()) {
                    return -1
                }
                var firstOccurrenceOfWord = -1
                if (!TextUtils.isEmpty(typedWord)) {
                    firstOccurrenceOfWord = removeSuggestedWordInfoFromList(
                            typedWord!!, candidates, -1 /* startIndexExclusive */)
                }
                for (i in candidates.indices) {
                    removeSuggestedWordInfoFromList(
                            candidates[i].word, candidates, i /* startIndexExclusive */)
                }
                return firstOccurrenceOfWord
            }

            private fun removeSuggestedWordInfoFromList(
                    word: String,
                    candidates: ArrayList<SuggestedWordInfo>,
                    startIndexExclusive: Int): Int {
                var firstOccurrenceOfWord = -1
                var i = startIndexExclusive + 1
                while (i < candidates.size) {
                    val previous = candidates[i]
                    if (word == previous.word) {
                        if (firstOccurrenceOfWord == -1) {
                            firstOccurrenceOfWord = i
                        }
                        candidates.removeAt(i)
                        --i
                    }
                    ++i
                }
                return firstOccurrenceOfWord
            }
        }
    }

    companion object {
        val INDEX_OF_TYPED_WORD = 0
        val INDEX_OF_AUTO_CORRECTION = 1
        val NOT_A_SEQUENCE_NUMBER = -1

        val INPUT_STYLE_NONE = 0
        val INPUT_STYLE_TYPING = 1
        val INPUT_STYLE_UPDATE_BATCH = 2
        val INPUT_STYLE_TAIL_BATCH = 3
        val INPUT_STYLE_APPLICATION_SPECIFIED = 4
        val INPUT_STYLE_RECORRECTION = 5
        val INPUT_STYLE_PREDICTION = 6
        val INPUT_STYLE_BEGINNING_OF_SENTENCE_PREDICTION = 7

        // The maximum number of suggestions available.
        val MAX_SUGGESTIONS = 18

        private val EMPTY_WORD_INFO_LIST = ArrayList<SuggestedWordInfo>(0)
        /* rawSuggestions *//* typedWord *//* typedWordValid *//* willAutoCorrect *//* isObsoleteSuggestions */ val emptyInstance = SuggestedWords(
                EMPTY_WORD_INFO_LIST, null, null,
                false, false,
                false, INPUT_STYLE_NONE, NOT_A_SEQUENCE_NUMBER)

        fun getFromApplicationSpecifiedCompletions(
                infos: Array<CompletionInfo>): ArrayList<SuggestedWordInfo> {
            val result = ArrayList<SuggestedWordInfo>()
            for (info in infos) {
                if (null == info || null == info.text) {
                    continue
                }
                result.add(SuggestedWordInfo(info))
            }
            return result
        }

        // Should get rid of the first one (what the user typed previously) from suggestions
        // and replace it with what the user currently typed.
        fun getTypedWordAndPreviousSuggestions(
                typedWordInfo: SuggestedWordInfo,
                previousSuggestions: SuggestedWords): ArrayList<SuggestedWordInfo> {
            val suggestionsList = ArrayList<SuggestedWordInfo>()
            val alreadySeen = HashSet<String>()
            suggestionsList.add(typedWordInfo)
            alreadySeen.add(typedWordInfo.word)
            val previousSize = previousSuggestions.size()
            for (index in 1 until previousSize) {
                val prevWordInfo = previousSuggestions.getInfo(index)
                val prevWord = prevWordInfo!!.word
                // Filter out duplicate suggestions.
                if (!alreadySeen.contains(prevWord)) {
                    suggestionsList.add(prevWordInfo)
                    alreadySeen.add(prevWord)
                }
            }
            return suggestionsList
        }

        private fun isPrediction(inputStyle: Int): Boolean {
            return INPUT_STYLE_PREDICTION == inputStyle || INPUT_STYLE_BEGINNING_OF_SENTENCE_PREDICTION == inputStyle
        }
    }
}
