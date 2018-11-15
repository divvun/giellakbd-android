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

class DivvunDictionary: Dictionary(Dictionary.TYPE_MAIN, Locale("se", "")) {
    private val tag = createTag(this)
    private val speller: DivvunSpell by lazy { DivvunUtils.getSpeller(mLocale!!) }

    override fun getSuggestions(composedData: ComposedData, ngramContext: NgramContext, proximityInfoHandle: Long, settingsValuesForSuggestion: SettingsValuesForSuggestion, sessionId: Int, weightForLocale: Float, inOutWeightOfLangModelVsSpatialModel: FloatArray): ArrayList<SuggestedWords.SuggestedWordInfo> {
        val suggestions = speller.suggest(composedData.mTypedWord, N_BEST_SUGGESTION_SIZE)

        Log.d(tag, suggestions.toString())

        val result = suggestions.mapIndexed { index, suggestion ->
            SuggestedWordInfo(suggestion, ngramContext!!.extractPrevWordsContext(),
                    suggestions.size - index, SuggestedWordInfo.KIND_CORRECTION, this,
                    SuggestedWordInfo.NOT_AN_INDEX, SuggestedWordInfo.NOT_A_CONFIDENCE)
        }

        return ArrayList(result)
    }

    override fun isInDictionary(word: String): Boolean {

        return speller.isCorrect(word)
    }

    companion object {
        const val N_BEST_SUGGESTION_SIZE = 5L
    }
}