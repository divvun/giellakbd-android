package no.divvun.packageobserver

import android.content.Context
import androidx.work.WorkInfo
import no.divvun.pahkat.WORKMANAGER_TAG_UPDATE
import no.divvun.pahkat.workManager
import timber.log.Timber
import java.io.File

object PackageObserver {
    var listener: OnPackageUpdateListener? = null
    private var lastModified = 0L

    fun init(context: Context, path: String) {
        Timber.d("Init")
        context.workManager().getWorkInfosByTagLiveData(WORKMANAGER_TAG_UPDATE).observeForever {
            val workInfo = it.firstOrNull() ?: return@observeForever

            if (workInfo.state == WorkInfo.State.ENQUEUED) {
                val spellerFile = File(path)
                if (spellerFile.exists() && lastModified != spellerFile.lastModified()) {
                    lastModified = spellerFile.lastModified()
                    Timber.i("OnPackage Update!")
                    listener?.onPackageUpdate()
                }
            }
        }
    }
}