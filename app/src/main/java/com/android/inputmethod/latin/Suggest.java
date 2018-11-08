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

package com.android.inputmethod.latin;

import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.latin.define.DebugFlags;
import com.android.inputmethod.latin.settings.SettingsValuesForSuggestion;

import java.util.HashMap;
import java.util.Locale;

/**
 * This class loads a dictionary and provides a list of suggestions for a given sequence of
 * characters. This includes corrections and completions.
 */
public final class Suggest {
    public static final String TAG = Suggest.class.getSimpleName();

    // Session id for
    // {@link #getSuggestedWords(WordComposer,String,ProximityInfo,boolean,int)}.
    // We are sharing the same ID between typing and gesture to save RAM footprint.
    public static final int SESSION_ID_TYPING = 0;
    public static final int SESSION_ID_GESTURE = 0;

    // Close to -2**31
    private static final int SUPPRESS_SUGGEST_THRESHOLD = -2000000000;

    private static final boolean DBG = DebugFlags.DEBUG_ENABLED;
    private final DictionaryFacilitator mDictionaryFacilitator;

    private static final int MAXIMUM_AUTO_CORRECT_LENGTH_FOR_GERMAN = 12;
    private static final HashMap<String, Integer> sLanguageToMaximumAutoCorrectionWithSpaceLength =
            new HashMap<>();
    static {
        // TODO: should we add Finnish here?
        // TODO: This should not be hardcoded here but be written in the dictionary header
        sLanguageToMaximumAutoCorrectionWithSpaceLength.put(Locale.GERMAN.getLanguage(),
                MAXIMUM_AUTO_CORRECT_LENGTH_FOR_GERMAN);
    }

    private float mAutoCorrectionThreshold;
    private float mPlausibilityThreshold;

    public Suggest(final DictionaryFacilitator dictionaryFacilitator) {
        mDictionaryFacilitator = dictionaryFacilitator;
    }

    /**
     * Set the normalized-score threshold for a suggestion to be considered strong enough that we
     * will auto-correct to this.
     * @param threshold the threshold
     */
    public void setAutoCorrectionThreshold(final float threshold) {
        mAutoCorrectionThreshold = threshold;
    }

    /**
     * Set the normalized-score threshold for what we consider a "plausible" suggestion, in
     * the same dimension as the auto-correction threshold.
     * @param threshold the threshold
     */
    public void setPlausibilityThreshold(final float threshold) {
        mPlausibilityThreshold = threshold;
    }

    public interface OnGetSuggestedWordsCallback {
        void onGetSuggestedWords(final SuggestedWords suggestedWords);
    }

    public void getSuggestedWords(final WordComposer wordComposer,
            final NgramContext ngramContext, final Keyboard keyboard,
            final SettingsValuesForSuggestion settingsValuesForSuggestion,
            final boolean isCorrectionEnabled, final int inputStyle, final int sequenceNumber,
            final OnGetSuggestedWordsCallback callback) {
        // TODO(bbqsrc)
//        if (wordComposer.isBatchMode()) {
//            getSuggestedWordsForBatchInput(wordComposer, ngramContext, keyboard,
//                    settingsValuesForSuggestion, inputStyle, sequenceNumber, callback);
//        } else {
//            getSuggestedWordsForNonBatchInput(wordComposer, ngramContext, keyboard,
//                    settingsValuesForSuggestion, inputStyle, isCorrectionEnabled,
//                    sequenceNumber, callback);
//        }
    }
}
