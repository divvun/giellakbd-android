package no.divvun.packageobserver

import android.content.Context
import io.sentry.Sentry
import no.divvun.divvunspell.ThfstChunkedBoxSpellerArchive
import no.divvun.spellers
import timber.log.Timber
import java.util.*

class SpellerArchiveWatcher(private val context: Context, private val locale: Locale) : OnPackageUpdateListener {
    var archive: ThfstChunkedBoxSpellerArchive? = null

    init {
        updateArchive()
        PackageObserver.listener = this
    }

    private fun updateArchive() {
        Timber.d("Updating speller archive")
        archive = try {
            Timber.d("Retrieving speller for languageTag: ${locale.language}")
            val spellerPath = spellers[locale.language]?.spellerPath(context)
            Timber.d("Speller path: $spellerPath")
            if(spellerPath != null){
                Timber.d("Speller path found resolved: $spellerPath")
                Timber.d("Opening archive")
                ThfstChunkedBoxSpellerArchive.open(spellerPath)
            } else {
                Timber.d("No speller found for ${locale.toLanguageTag()} in $spellers")
                null
            }
        } catch (ex: Exception) {
            Timber.e("Failed to open archive $ex")
            Sentry.capture(ex)
            null
        }
    }

    override fun onPackageUpdate() {
        updateArchive()
    }
}
