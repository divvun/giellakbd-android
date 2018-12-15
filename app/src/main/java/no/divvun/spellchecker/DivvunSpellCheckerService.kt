package no.divvun.spellchecker

import android.service.textservice.SpellCheckerService
import android.util.Log
import android.view.textservice.SentenceSuggestionsInfo
import android.view.textservice.SuggestionsInfo
import android.view.textservice.TextInfo
import no.divvun.DivvunSpell
import no.divvun.DivvunUtils
import no.divvun.createTag
import java.util.*

class DivvunSpellCheckerService: SpellCheckerService(){
    private val tag = createTag(this)

    override fun onCreate() {
        super.onCreate()
        Log.d(tag, "onCreate")
        DivvunUtils.initialize(this)
    }

    override fun createSession(): Session {
        Log.d(tag, "createSession")
        return DivvunSpellCheckerSession()
    }

    class DivvunSpellCheckerSession: Session() {
        private val tag = javaClass.simpleName!!
        private var speller: DivvunSpell? = null

        override fun onCreate() {
            Log.d(tag, "onCreate")
            speller = DivvunUtils.getSpeller(Locale(locale))
        }

        override fun onGetSuggestions(textInfo: TextInfo?, suggestionsLimit: Int): SuggestionsInfo {
            val speller = this.speller ?: return SuggestionsInfo(SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY, arrayOfNulls(0))

            Log.d(tag, "onGetSuggestions()")

            // Get the word
            val word = textInfo!!.text
            Log.d(tag, "word: $word")

            // Check if the word is spelled correctly.
            if (speller.isCorrect(word)) {
                Log.d(tag, "$word isCorrect")
                return SuggestionsInfo(SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY, arrayOfNulls(0))
            }
            // If the word isn't correct, query for suggestions
            val suggestions = speller.suggest(word)

            val attrs = SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO
            return SuggestionsInfo(attrs, suggestions.toTypedArray())
        }

        override fun onGetSentenceSuggestionsMultiple(textInfos: Array<out TextInfo>?, suggestionsLimit: Int): Array<SentenceSuggestionsInfo> {
            Log.d(tag, "onGetSentenceSuggestionsMultiple()")
            return super.onGetSentenceSuggestionsMultiple(textInfos, suggestionsLimit)
        }

        override fun onGetSuggestionsMultiple(textInfos: Array<out TextInfo>?, suggestionsLimit: Int, sequentialWords: Boolean): Array<SuggestionsInfo> {
            Log.d(tag, "onGetSuggestionsMultiple()")
            return super.onGetSuggestionsMultiple(textInfos, suggestionsLimit, sequentialWords)
        }
    }

}