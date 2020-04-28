package no.divvun.packageobserver

import android.content.Context
import androidx.work.WorkInfo
import no.divvun.pahkat.WORKMANAGER_TAG_UPDATE
import no.divvun.pahkat.workManager
import timber.log.Timber

object PackageObserver {
    var listener: OnPackageUpdateListener? = null
//    private var lastModified = 0L

    fun init(context: Context) {
        Timber.d("Init")
        context.workManager().getWorkInfosByTagLiveData(WORKMANAGER_TAG_UPDATE).observeForever {
            Timber.d("WorkInfos $it")
            val workInfo = it.firstOrNull() ?: return@observeForever
            Timber.d("WorkInfo $workInfo")

            if (workInfo.state == WorkInfo.State.ENQUEUED) {
                /**
                val spellerFile = File(path)
                if (spellerFile.exists() && lastModified != spellerFile.lastModified()) {
                lastModified = spellerFile.lastModified()
                 */
                Timber.i("OnPackages Update!")
                listener?.onPackageUpdate()
            }
        }
    }
}