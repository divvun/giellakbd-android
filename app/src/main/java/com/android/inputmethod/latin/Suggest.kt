/*
 * Copyright (C) 2008 The Android Open Source Project
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

import android.util.Log

import com.android.inputmethod.keyboard.Keyboard
import com.android.inputmethod.latin.define.DebugFlags
import com.android.inputmethod.latin.settings.SettingsValuesForSuggestion

import java.util.HashMap
import java.util.Locale

/**
 * This class loads a dictionary and provides a list of suggestions for a given sequence of
 * characters. This includes corrections and completions.
 */
class Suggest(private val mDictionaryFacilitator: DictionaryFacilitator?) {

    private var mAutoCorrectionThreshold: Float = 0.toFloat()
    private var mPlausibilityThreshold: Float = 0.toFloat()

    /**
     * Set the normalized-score threshold for a suggestion to be considered strong enough that we
     * will auto-correct to this.
     * @param threshold the threshold
     */
    fun setAutoCorrectionThreshold(threshold: Float) {
        mAutoCorrectionThreshold = threshold
    }

    /**
     * Set the normalized-score threshold for what we consider a "plausible" suggestion, in
     * the same dimension as the auto-correction threshold.
     * @param threshold the threshold
     */
    fun setPlausibilityThreshold(threshold: Float) {
        mPlausibilityThreshold = threshold
    }

    interface OnGetSuggestedWordsCallback {
        fun onGetSuggestedWords(suggestedWords: SuggestedWords)
    }

    fun getSuggestedWords(wordComposer: WordComposer,
                          ngramContext: NgramContext, keyboard: Keyboard,
                          settingsValuesForSuggestion: SettingsValuesForSuggestion,
                          isCorrectionEnabled: Boolean, inputStyle: Int, sequenceNumber: Int,
                          callback: OnGetSuggestedWordsCallback) {

        val composedData = wordComposer.composedDataSnapshot
        val suggestionResults = mDictionaryFacilitator!!.getSuggestionResults(
                        composedData,
                        ngramContext,
                        keyboard,
                        settingsValuesForSuggestion,
                        SESSION_ID_GESTURE,
                        inputStyle)

        val suggestedWords = SuggestedWords(ArrayList(suggestionResults),
                suggestionResults.mRawSuggestions,
                null,
                mDictionaryFacilitator.isValidSpellingWord(composedData.mTypedWord),
                        false, false, inputStyle, sequenceNumber)

        callback.onGetSuggestedWords(suggestedWords)


        // TODO(bbqsrc)
        // Batch mode currently available needs to be implemented

        //        if (wordComposer.isBatchMode()) {
        //            getSuggestedWordsForBatchInput(wordComposer, ngramContext, keyboard,
        //                    settingsValuesForSuggestion, inputStyle, sequenceNumber, callback);
        //        } else {
        //            getSuggestedWordsForNonBatchInput(wordComposer, ngramContext, keyboard,
        //                    settingsValuesForSuggestion, inputStyle, isCorrectionEnabled,
        //                    sequenceNumber, callback);
        //        }
    }

    companion object {
        val TAG = Suggest::class.java.simpleName

        // Session id for
        // {@link #getSuggestedWords(WordComposer,String,ProximityInfo,boolean,int)}.
        // We are sharing the same ID between typing and gesture to save RAM footprint.
        val SESSION_ID_TYPING = 0
        val SESSION_ID_GESTURE = 0

        // Close to -2**31
        private val SUPPRESS_SUGGEST_THRESHOLD = -2000000000

        private val DBG = DebugFlags.DEBUG_ENABLED

        private val MAXIMUM_AUTO_CORRECT_LENGTH_FOR_GERMAN = 12
        private val sLanguageToMaximumAutoCorrectionWithSpaceLength = HashMap<String, Int>()

        init {
            // TODO: should we add Finnish here?
            // TODO: This should not be hardcoded here but be written in the dictionary header
            sLanguageToMaximumAutoCorrectionWithSpaceLength[Locale.GERMAN.language] = MAXIMUM_AUTO_CORRECT_LENGTH_FOR_GERMAN
        }
    }
}
