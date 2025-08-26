package com.android.inputmethod.ui.personaldictionary.language

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodSubtype
import com.android.inputmethod.latin.utils.SubtypeLocaleUtils
import com.android.inputmethod.ui.personaldictionary.language.adapter.LanguageWordViewState
import com.android.inputmethod.usecases.LanguagesUseCase
import io.reactivex.Observable
import io.reactivex.ObservableTransformer
import no.divvun.dictionary.personal.Language
import no.divvun.toLanguageTag
import timber.log.Timber
import java.util.*


class LanguagePresenter(
        private val view: LanguageView,
        private val languageUseCase: LanguagesUseCase,
        private val context: Context
) {
    private val initialViewState: LanguageViewState = LanguageViewState()

    val states by lazy { start() }

    fun start(): Observable<LanguageViewState> {
        return Observable.merge(
                languageUseCase.execute().map { LanguageUpdate.Lang(it) },
                view.events().compose(uiTransformer))
                .scan(initialViewState, { state: LanguageViewState, event: LanguageUpdate ->
                    val subtypes = resolveMetaData(context)
                    Timber.d("RESOLVED SUBTYPES $subtypes")
                    when (event) {
                        is LanguageUpdate.Lang -> {
                            state.copy(languages = event.languages.map {
                                Timber.d("Language UPDATE: ${it.language}, ${it.country}, ${it.variant}")
                                val locale = Locale(it.language, it.country, it.variant).toString().toLanguageTag()

                                Timber.d("LANGUAGE: $locale")
                                Timber.d("LANGUAGE: ${subtypes[locale]}")
                                Timber.d("LANGUAGE: ${SubtypeLocaleUtils.getSubtypeLocaleDisplayName(locale)}")
                                val displayName = subtypes[locale]
                                        ?: SubtypeLocaleUtils.getSubtypeLocaleDisplayName(locale)

                                Timber.d("Language UPDATE: ${it.language}, ${it.country}, ${it.variant} $locale $displayName")
                                LanguageWordViewState(it.languageId, displayName, it.language, it.country, it.variant)
                            }.sortedBy { it.language.lowercase(Locale.getDefault()) })
                        }
                    }
                })
                .replay(1)
                .autoConnect()
    }

    private val uiTransformer = ObservableTransformer<LanguageEvent, LanguageUpdate> {
        it.flatMap { languageEvent ->
            when (languageEvent) {
                is LanguageEvent.OnLanguageSelected -> {
                    view.navigateToDictionary(languageEvent.languageId, languageEvent.language)
                    Observable.empty<LanguageUpdate>()
                }
            }
        }
    }

}


sealed class LanguageUpdate {
    data class Lang(val languages: List<Language>) : LanguageUpdate()
}


private fun resolveMetaData(context: Context): Map<String, String> {
    val intent = Intent(context, com.android.inputmethod.latin.LatinIME::class.java)
    val resolveInfo = context.packageManager.resolveService(intent, PackageManager.GET_META_DATA)
    val appInfo = context.packageManager.getApplicationInfo(context.packageName, 0)

    val imi = InputMethodInfo(context, resolveInfo)
    return imi.subtypes().map { it.locale.toLanguageTag() to it.getDisplayName(context, context.packageName, appInfo).toString() }.toMap()
}

fun InputMethodInfo.subtypes(): List<InputMethodSubtype> {
    return (0 until subtypeCount).map { getSubtypeAt(it) }
}
