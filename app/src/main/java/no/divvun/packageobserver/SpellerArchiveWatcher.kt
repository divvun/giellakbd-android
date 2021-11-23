package no.divvun.packageobserver

import android.content.Context
import io.sentry.Sentry
import no.divvun.Spellers
import no.divvun.divvunspell.ThfstChunkedBoxSpellerArchive
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.*

class SpellerArchiveWatcher(private val context: Context, private val locale: Locale) : OnPackageUpdateListener {
    var archive: ThfstChunkedBoxSpellerArchive? = null
    var hasMetadata = true

    init {
        PackageObserver.listener = this
        updateArchive()
    }

    private fun cleanupInvalidFile(path: String) {
        val file = File(path)

        try {
            if (file.exists()) {
                Timber.d("File exists at $path")
            }

            file.delete()
        } catch(e: SecurityException) {
            Timber.e(e)
        } catch(e: IOException) {
            Timber.e(e)
        }
    }

    private fun updateArchive() {
        if (!hasMetadata) {
            // Don't bother trying to load an archive again since it doesn't exist.
            return
        }

        Timber.d("Retrieving speller for languageTag: ${locale.language}")
        val spellerPath = Spellers[locale.toLanguageTag()]?.spellerPath(context)

        if (spellerPath == null) {
            Timber.w("No speller found for ${locale.toLanguageTag()} in ${Spellers.config}")
            hasMetadata = false
            return
        }

        Timber.d("Speller path: $spellerPath")
        Timber.d("Updating speller archive")

        archive = try {
            Timber.d("Opening archive")
            ThfstChunkedBoxSpellerArchive.open(spellerPath)
        } catch (ex: Exception) {
            Timber.e("Failed to open archive $ex")
            Sentry.captureException(ex)
            cleanupInvalidFile(spellerPath)
            null
        }
    }

    override fun onPackageUpdate() {
        updateArchive()
    }
}
