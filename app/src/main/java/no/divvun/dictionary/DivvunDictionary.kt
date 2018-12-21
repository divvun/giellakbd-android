package no.divvun.dictionary

import android.util.Log
import com.android.inputmethod.latin.Dictionary
import com.android.inputmethod.latin.NgramContext
import com.android.inputmethod.latin.SuggestedWords
import com.android.inputmethod.latin.common.ComposedData
import com.android.inputmethod.latin.settings.SettingsValuesForSuggestion
import java.util.*
import com.android.inputmethod.latin.SuggestedWords.SuggestedWordInfo
import no.divvun.DivvunSpell
import no.divvun.DivvunUtils
import no.divvun.createTag
import kotlin.collections.ArrayList

class DivvunDictionary(locale: Locale?): Dictionary(Dictionary.TYPE_MAIN, locale){
    private val tag = createTag(this)
    private val speller: DivvunSpell? by lazy { DivvunUtils.getSpeller(mLocale) }

    override fun getSuggestions(composedData: ComposedData, ngramContext: NgramContext, proximityInfoHandle: Long, settingsValuesForSuggestion: SettingsValuesForSuggestion, sessionId: Int, weightForLocale: Float, inOutWeightOfLangModelVsSpatialModel: FloatArray): ArrayList<SuggestedWords.SuggestedWordInfo> {
        val speller = this.speller ?: return ArrayList()

        val suggestions = mutableListOf(composedData.mTypedWord)
        speller.suggest(composedData.mTypedWord, N_BEST_SUGGESTION_SIZE, MAX_WEIGHT).forEach {
            suggestions.add(it)
        }

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