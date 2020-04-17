package no.divvun.dictionary

import android.content.Context
import com.android.inputmethod.latin.Dictionary
import com.android.inputmethod.latin.NgramContext
import com.android.inputmethod.latin.SuggestedWords
import com.android.inputmethod.latin.SuggestedWords.SuggestedWordInfo
import com.android.inputmethod.latin.common.ComposedData
import com.android.inputmethod.latin.settings.SettingsValuesForSuggestion
import no.divvun.divvunspell.SpellerConfig
import no.divvun.packageId
import no.divvun.packagePath
import timber.log.Timber
import java.util.*
import kotlin.collections.ArrayList

class DivvunDictionary(private val context: Context?, locale: Locale?) : Dictionary(TYPE_MAIN, locale) {

    private val spellerArchiveWatcher: SpellerArchiveWatcher? = context?.let { SpellerArchiveWatcher(path = packagePath(it, packageId)) }
    private val speller = spellerArchiveWatcher?.archive?.speller()

    init {
        Timber.d("DivvunDictionaryCreated")

    }

    override fun getSuggestions(composedData: ComposedData, ngramContext: NgramContext, proximityInfoHandle: Long, settingsValuesForSuggestion: SettingsValuesForSuggestion, sessionId: Int, weightForLocale: Float, inOutWeightOfLangModelVsSpatialModel: FloatArray): ArrayList<SuggestedWords.SuggestedWordInfo> {
        Timber.d("getSuggestions")
        val speller = this.speller ?: return ArrayList()
        val word = composedData.mTypedWord.trim()

        if (word == "") {
            Timber.wtf("Word was invalid!")
            return ArrayList()
        }

        Timber.d("Got speller")

        val suggestions = mutableListOf(composedData.mTypedWord)
        val config = SpellerConfig(nBest = N_BEST_SUGGESTION_SIZE, maxWeight = MAX_WEIGHT)
        speller.suggest(word, config).forEach {
            suggestions.add(it)
        }

        Timber.d("$suggestions")

        val result = suggestions.mapIndexed { index, suggestion ->
            if (index == 0) {
                SuggestedWordInfo(suggestion, ngramContext.extractPrevWordsContext(),
                        suggestions.size - index, SuggestedWordInfo.KIND_CORRECTION, this,
                        SuggestedWordInfo.NOT_AN_INDEX, SuggestedWordInfo.NOT_A_CONFIDENCE)
            } else {
                SuggestedWordInfo(suggestion, ngramContext.extractPrevWordsContext(),
                        SuggestedWordInfo.MAX_SCORE, SuggestedWordInfo.KIND_TYPED, this,
                        SuggestedWordInfo.NOT_AN_INDEX, SuggestedWordInfo.NOT_A_CONFIDENCE)
            }
        }

        return ArrayList(result)
    }

    override fun isInDictionary(word: String): Boolean {
        val speller = this.speller ?: return true

        return speller.isCorrect(word)
    }

    companion object {
        const val N_BEST_SUGGESTION_SIZE = 3L
        const val MAX_WEIGHT = 4999.99f
    }
}