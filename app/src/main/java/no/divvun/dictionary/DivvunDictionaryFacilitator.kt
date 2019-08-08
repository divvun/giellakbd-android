package no.divvun.dictionary

import android.content.Context
import android.util.LruCache
import com.android.inputmethod.keyboard.Keyboard
import com.android.inputmethod.latin.DictionaryFacilitator
import com.android.inputmethod.latin.DictionaryStats
import com.android.inputmethod.latin.NgramContext
import com.android.inputmethod.latin.common.ComposedData
import com.android.inputmethod.latin.settings.SettingsValuesForSuggestion
import com.android.inputmethod.latin.utils.SuggestionResults
import timber.log.Timber
import no.divvun.dictionary.personal.PersonalDictionary
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit

class DivvunDictionaryFacilitator : DictionaryFacilitator {
    private var isActive = false

    private var dictionary = DivvunDictionary(null, null)
    private lateinit var personalDictionary: PersonalDictionary

    // STUB
    override fun setValidSpellingWordReadCache(cache: LruCache<String, Boolean>) {
        Timber.d("setValidSpellingWordReadCache")
    }

    // STUB
    override fun setValidSpellingWordWriteCache(cache: LruCache<String, Boolean>?) {
        Timber.d("setValidSpellingWordWriteCache")
    }

    override fun isForLocale(locale: Locale?): Boolean {
        Timber.d("isForLocale $locale")
        return dictionary.mLocale == locale
    }

    // STUB
    override fun isForAccount(account: String?): Boolean {
        Timber.d("isForAccount")
        return false
    }

    // STUB
    override fun onStartInput() {
        Timber.d("onStartInput")
        isActive = true
    }

    // STUB
    override fun onFinishInput(context: Context?) {
        Timber.d("onFinishInput")
        isActive = false
    }

    // STUB
    override fun isActive(): Boolean {
        Timber.d("isActive")
        return isActive
    }

    override fun getLocale(): Locale {
        Timber.d("getLocale")
        return dictionary.mLocale!!
    }

    // STUB
    override fun usesContacts(): Boolean {
        Timber.d("usesContacts")
        return false
    }

    // STUB
    override fun getAccount(): String {
        Timber.d("getAccount")
        return ""
    }

    // STUB
    override fun resetDictionaries(context: Context?, newLocale: Locale?, useContactsDict: Boolean, usePersonalizedDicts: Boolean, forceReloadMainDictionary: Boolean, account: String?, dictNamePrefix: String?, listener: DictionaryFacilitator.DictionaryInitializationListener?) {
        Timber.d("resetDictionaries")
        context?.let {
            dictionary = DivvunDictionary(it, newLocale)
            personalDictionary = PersonalDictionary(it, newLocale!!)
        }
    }

    // STUB
    override fun resetDictionariesForTesting(context: Context?, locale: Locale?, dictionaryTypes: ArrayList<String>?, dictionaryFiles: HashMap<String, File>?, additionalDictAttributes: MutableMap<String, MutableMap<String, String>>?, account: String?) {
        Timber.d("resetDictionariesForTesting")
    }

    // STUB
    override fun closeDictionaries() {
        Timber.d("closeDictionaries")
    }

    // STUB
    override fun hasAtLeastOneInitializedMainDictionary(): Boolean {
        Timber.d("hasAtLeastOneInitializedMainDictionary")
        return dictionary.isInitialized
    }

    // STUB
    override fun hasAtLeastOneUninitializedMainDictionary(): Boolean {
        Timber.d("hasAtLeastOneUninitializedMainDictionary")
        return !dictionary.isInitialized
    }

    // STUB
    override fun waitForLoadingMainDictionaries(timeout: Long, unit: TimeUnit?) {
        Timber.d("waitForLoadingMainDictionaries")
    }

    // STUB
    override fun waitForLoadingDictionariesForTesting(timeout: Long, unit: TimeUnit?) {
        Timber.d("waitForLoadingDictiionariesForTesting")
    }

    // STUB
    override fun addToUserHistory(word: String, wasAutoCapitalized: Boolean, ngramContext: NgramContext, timeStampInSeconds: Long, blockPotentiallyOffensive: Boolean) {
        Timber.d("addToUserHistory")
        if (!dictionary.isInDictionary(word)) {
            Timber.d("Adding non known word: $word")
            personalDictionary.learn(word)
        }

        val previousWords = ngramContext.extractPrevWordsContextArray().toList().filter { it != NgramContext.BEGINNING_OF_SENTENCE_TAG }.takeLast(2)
        personalDictionary.processContext(previousWords, word)
    }

    // STUB
    override fun unlearnFromUserHistory(word: String?, ngramContext: NgramContext, timeStampInSeconds: Long, eventType: Int) {
        Timber.d("unlearnFromUserHistory")
        word?.let {
            personalDictionary.unlearn(word)
        }
    }

    override fun getSuggestionResults(composedData: ComposedData, ngramContext: NgramContext, keyboard: Keyboard, settingsValuesForSuggestion: SettingsValuesForSuggestion, sessionId: Int, inputStyle: Int): SuggestionResults {
        val divvunSuggestions = dictionary.getSuggestions(composedData, ngramContext, 0, settingsValuesForSuggestion, sessionId, 0f, FloatArray(0))
        val personalSuggestions = personalDictionary.getSuggestions(composedData, ngramContext, 0, settingsValuesForSuggestion, sessionId, 0f, FloatArray(0))

        val suggestionResults = SuggestionResults(divvunSuggestions.size + personalSuggestions.size, ngramContext.isBeginningOfSentenceContext, true)

        // Add all our suggestions
        suggestionResults.addAll(divvunSuggestions)
        suggestionResults.addAll(personalSuggestions)

        Timber.d("Personal suggestions: $personalSuggestions")
        Timber.d("All suggestions: $suggestionResults")

        return suggestionResults
    }

    override fun isValidSpellingWord(word: String): Boolean = dictionary.isValidWord(word)
    override fun isValidSuggestionWord(word: String): Boolean = dictionary.isValidWord(word)

    // STUB
    override fun clearUserHistoryDictionary(context: Context?): Boolean {
        Timber.d("clearUserHistoryDictionary")
        return true
    }

    // STUB
    override fun dump(context: Context?): String {
        Timber.d("dump")
        return ""
    }

    // STUB
    override fun dumpDictionaryForDebug(dictName: String?) {
        Timber.d("dumpDictionaryForDebug")
    }

    // STUB
    override fun getDictionaryStats(context: Context?): MutableList<DictionaryStats> {
        Timber.d("getDictionaryStats")
        return mutableListOf()
    }

}
