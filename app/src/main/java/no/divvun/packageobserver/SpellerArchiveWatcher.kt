package no.divvun.packageobserver

import io.sentry.Sentry
import no.divvun.divvunspell.ThfstChunkedBoxSpellerArchive
import timber.log.Timber

class SpellerArchiveWatcher(private val spellerPath: String) : OnPackageUpdateListener {
    var archive: ThfstChunkedBoxSpellerArchive? = null

    init {
        updateArchive()
        PackageObserver.listener = this
    }

    private fun updateArchive() {
        archive = try {
            Timber.d("Try open archive $spellerPath")
            ThfstChunkedBoxSpellerArchive.open(spellerPath)
        } catch (ex: Exception) {
            Sentry.capture(ex)
            null
        }
    }

    override fun onPackageUpdate() {
        updateArchive()
    }
}
