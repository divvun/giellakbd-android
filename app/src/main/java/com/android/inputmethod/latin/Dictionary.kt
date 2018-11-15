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

import com.android.inputmethod.annotations.UsedForTesting
import com.android.inputmethod.latin.SuggestedWords.SuggestedWordInfo
import com.android.inputmethod.latin.common.ComposedData
import com.android.inputmethod.latin.settings.SettingsValuesForSuggestion

import java.util.ArrayList
import java.util.Locale
import java.util.Arrays
import java.util.HashSet

/**
 * Abstract base class for a dictionary that can do a fuzzy search for words based on a set of key
 * strokes.
 */
abstract class Dictionary(val mDictType: String, // The locale for this dictionary. May be null if unknown (phony dictionary for example).
                          val mLocale: Locale?) {

    /**
     * Subclasses may override to indicate that this Dictionary is not yet properly initialized.
     */
    open val isInitialized: Boolean
        get() = true

    /**
     * Whether this dictionary is based on data specific to the user, e.g., the user's contacts.
     * @return Whether this dictionary is specific to the user.
     */
    val isUserSpecific: Boolean
        get() = sUserSpecificDictionaryTypes.contains(mDictType)

    /**
     * Searches for suggestions for a given context.
     * @param composedData the key sequence to match with coordinate info
     * @param ngramContext the context for n-gram.
     * @param proximityInfoHandle the handle for key proximity. Is ignored by some implementations.
     * @param settingsValuesForSuggestion the settings values used for the suggestion.
     * @param sessionId the session id.
     * @param weightForLocale the weight given to this locale, to multiply the output scores for
     * multilingual input.
     * @param inOutWeightOfLangModelVsSpatialModel the weight of the language model as a ratio of
     * the spatial model, used for generating suggestions. inOutWeightOfLangModelVsSpatialModel is
     * a float array that has only one element. This can be updated when a different value is used.
     * @return the list of suggestions (possibly null if none)
     */
    abstract fun getSuggestions(composedData: ComposedData,
                                ngramContext: NgramContext, proximityInfoHandle: Long,
                                settingsValuesForSuggestion: SettingsValuesForSuggestion,
                                sessionId: Int, weightForLocale: Float,
                                inOutWeightOfLangModelVsSpatialModel: FloatArray): ArrayList<SuggestedWordInfo>

    /**
     * Checks if the given word has to be treated as a valid word. Please note that some
     * dictionaries have entries that should be treated as invalid words.
     * @param word the word to search for. The search should be case-insensitive.
     * @return true if the word is valid, false otherwise
     */
    fun isValidWord(word: String): Boolean {
        return isInDictionary(word)
    }

    /**
     * Checks if the given word is in the dictionary regardless of it being valid or not.
     */
    abstract fun isInDictionary(word: String): Boolean

    /**
     * Get the frequency of the word.
     * @param word the word to get the frequency of.
     */
    open fun getFrequency(word: String): Int {
        return NOT_A_PROBABILITY
    }

    /**
     * Get the maximum frequency of the word.
     * @param word the word to get the maximum frequency of.
     */
    open fun getMaxFrequencyOfExactMatches(word: String): Int {
        return NOT_A_PROBABILITY
    }

    /**
     * Compares the contents of the character array with the typed word and returns true if they
     * are the same.
     * @param word the array of characters that make up the word
     * @param length the number of valid characters in the character array
     * @param typedWord the word to compare with
     * @return true if they are the same, false otherwise.
     */
    protected fun same(word: CharArray, length: Int, typedWord: String): Boolean {
        if (typedWord.length != length) {
            return false
        }
        for (i in 0 until length) {
            if (word[i] != typedWord[i]) {
                return false
            }
        }
        return true
    }

    /**
     * Override to clean up any resources.
     */
    open fun close() {
        // empty base implementation
    }

    /**
     * Whether we think this suggestion should trigger an auto-commit. prevWord is the word
     * before the suggestion, so that we can use n-gram frequencies.
     * @param candidate The candidate suggestion, in whole (not only the first part).
     * @return whether we should auto-commit or not.
     */
    fun shouldAutoCommit(candidate: SuggestedWordInfo): Boolean {
        // If we don't have support for auto-commit, or if we don't know, we return false to
        // avoid auto-committing stuff. Implementations of the Dictionary class that know to
        // determine whether we should auto-commit will override this.
        return false
    }

    /**
     * Not a true dictionary. A placeholder used to indicate suggestions that don't come from any
     * real dictionary.
     */
    @UsedForTesting
    public class PhonyDictionary @UsedForTesting
    constructor(type: String) : Dictionary(type, null) {

        override fun getSuggestions(composedData: ComposedData,
                                    ngramContext: NgramContext, proximityInfoHandle: Long,
                                    settingsValuesForSuggestion: SettingsValuesForSuggestion,
                                    sessionId: Int, weightForLocale: Float,
                                    inOutWeightOfLangModelVsSpatialModel: FloatArray): ArrayList<SuggestedWordInfo> {
            return ArrayList()
        }

        override fun isInDictionary(word: String): Boolean {
            return false
        }
    }

    companion object {
        val NOT_A_PROBABILITY = -1
        val NOT_A_WEIGHT_OF_LANG_MODEL_VS_SPATIAL_MODEL = -1.0f

        // The following types do not actually come from real dictionary instances, so we create
        // corresponding instances.
        val TYPE_USER_TYPED = "user_typed"
        val DICTIONARY_USER_TYPED = PhonyDictionary(TYPE_USER_TYPED)

        val TYPE_USER_SHORTCUT = "user_shortcut"
        val DICTIONARY_USER_SHORTCUT = PhonyDictionary(TYPE_USER_SHORTCUT)

        val TYPE_APPLICATION_DEFINED = "application_defined"
        val DICTIONARY_APPLICATION_DEFINED = PhonyDictionary(TYPE_APPLICATION_DEFINED)

        val TYPE_HARDCODED = "hardcoded" // punctuation signs and such
        val DICTIONARY_HARDCODED = PhonyDictionary(TYPE_HARDCODED)

        // Spawned by resuming suggestions. Comes from a span that was in the TextView.
        val TYPE_RESUMED = "resumed"
        val DICTIONARY_RESUMED = PhonyDictionary(TYPE_RESUMED)

        // The following types of dictionary have actual functional instances. We don't need final
        // phony dictionary instances for them.
        val TYPE_MAIN = "main"
        val TYPE_CONTACTS = "contacts"
        // User dictionary, the system-managed one.
        val TYPE_USER = "user"
        // User history dictionary internal to LatinIME.
        val TYPE_USER_HISTORY = "history"

        /**
         * Set out of the dictionary types listed above that are based on data specific to the user,
         * e.g., the user's contacts.
         */
        private val sUserSpecificDictionaryTypes = HashSet(Arrays.asList(
                TYPE_USER_TYPED,
                TYPE_USER,
                TYPE_CONTACTS,
                TYPE_USER_HISTORY))
    }
}
