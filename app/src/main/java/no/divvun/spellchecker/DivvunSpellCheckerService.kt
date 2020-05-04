package no.divvun.spellchecker

import android.content.Context
import android.service.textservice.SpellCheckerService
import android.view.textservice.SentenceSuggestionsInfo
import android.view.textservice.SuggestionsInfo
import android.view.textservice.TextInfo
import no.divvun.packageobserver.SpellerArchiveWatcher
import no.divvun.toLocale
import timber.log.Timber
import java.util.*

class DivvunSpellCheckerService : SpellCheckerService() {
    override fun onCreate() {
        super.onCreate()
        Timber.d("onCreate")
    }

    override fun createSession(): Session {
        Timber.d("createSession")
        return DivvunSpellCheckerSession(this)
    }

    class DivvunSpellCheckerSession(private val context: Context) : Session() {

        private lateinit var spellerArchiveWatcher: SpellerArchiveWatcher
        private val speller
            get() = spellerArchiveWatcher.archive?.speller()

        override fun onCreate() {
            Timber.d("onCreate, locale: ${locale.toLocale()}")
            spellerArchiveWatcher = SpellerArchiveWatcher(context, locale.toLocale())
        }

        override fun onGetSuggestions(textInfo: TextInfo?, suggestionsLimit: Int): SuggestionsInfo {
            val speller = this.speller
                    ?: return SuggestionsInfo(SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY, arrayOfNulls(0))

            Timber.d("onGetSuggestions()")

            // Get the word
            val word = textInfo!!.text.trim()
            Timber.d("word: $word")

            if (word == "") {
                return SuggestionsInfo(SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY, arrayOfNulls(0))
            }

            // Check if the word is spelled correctly.
            if (speller.isCorrect(word)) {
                Timber.d("$word isCorrect")
                return SuggestionsInfo(SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY, arrayOfNulls(0))
            }
            // If the word isn't correct, query for suggestions
            val suggestions = speller.suggest(word)

            val attrs = SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO
            return SuggestionsInfo(attrs, suggestions.toTypedArray())
        }

        override fun onGetSentenceSuggestionsMultiple(textInfos: Array<out TextInfo>?, suggestionsLimit: Int): Array<SentenceSuggestionsInfo> {
            Timber.d("onGetSentenceSuggestionsMultiple()")
            return super.onGetSentenceSuggestionsMultiple(textInfos, suggestionsLimit)
        }

        override fun onGetSuggestionsMultiple(textInfos: Array<out TextInfo>?, suggestionsLimit: Int, sequentialWords: Boolean): Array<SuggestionsInfo> {
            Timber.d("onGetSuggestionsMultiple()")
            return super.onGetSuggestionsMultiple(textInfos, suggestionsLimit, sequentialWords)
        }
    }

}