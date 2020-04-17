package no.divvun.dictionary

import android.os.FileObserver
import io.sentry.Sentry
import no.divvun.divvunspell.ThfstChunkedBoxSpellerArchive
import timber.log.Timber

class SpellerArchiveWatcher(val path: String) {

    init {
        updateArchive()
    }

    val observer = object : FileObserver(path) {
        override fun onEvent(event: Int, path: String?) {
            when (event) {
                /** Event type: Data was written to a file  */
                MODIFY -> {
                    Timber.d("MODIFY")
                    updateArchive()
                }
                /** Event type: Someone had a file or directory open for writing, and closed it  */
                CLOSE_WRITE -> {
                    Timber.d("CLOSE_WRITE")
                }
                /** Event type: A new file or subdirectory was created under the monitored directory  */
                CREATE -> {
                    Timber.d("CREATE")
                    updateArchive()
                }
                /** Event type: A file was deleted from the monitored directory  */
                DELETE -> {
                    Timber.d("DELETE")
                    archive = null
                }
                /** Event type: The monitored file or directory was deleted; monitoring effectively stops  */
                DELETE_SELF -> {
                    Timber.d("DELETE_SELF")
                    archive = null
                }
                /** Event type: The monitored file or directory was moved; monitoring continues  */
                MOVE_SELF -> {
                    Timber.d("MOVE_SELF")
                    archive = null
                }
            }
        }
    }

    private fun updateArchive() {
        archive = try {
            ThfstChunkedBoxSpellerArchive.open(path)
        } catch (ex: Exception) {
            Sentry.capture(ex)
            null
        }
    }


    var archive: ThfstChunkedBoxSpellerArchive? = null
}
