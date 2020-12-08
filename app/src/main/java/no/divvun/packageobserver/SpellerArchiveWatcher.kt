package no.divvun.packageobserver

import android.content.Context
import io.sentry.Sentry
import no.divvun.Spellers
import no.divvun.divvunspell.ThfstChunkedBoxSpellerArchive
import timber.log.Timber
import java.util.*

class SpellerArchiveWatcher(private val context: Context, private val locale: Locale) : OnPackageUpdateListener {
    var archive: ThfstChunkedBoxSpellerArchive? = null

    init {
        PackageObserver.listener = this
        updateArchive()
    }

    private fun updateArchive() {
        Timber.d("Updating speller archive")
        archive = try {
            Timber.d("Retrieving speller for languageTag: ${locale.language}")
            val spellerPath = Spellers[locale.toLanguageTag()]?.spellerPath(context)
            Timber.d("Speller path: $spellerPath")

            if (spellerPath != null) {
                Timber.d("Speller path found resolved: $spellerPath")
                Timber.d("Opening archive")
                ThfstChunkedBoxSpellerArchive.open(spellerPath)
            } else {
                Timber.w("No speller found for ${locale.toLanguageTag()} in ${Spellers.config}")
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
