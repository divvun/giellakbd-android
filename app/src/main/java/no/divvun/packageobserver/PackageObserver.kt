package no.divvun.packageobserver

import timber.log.Timber
import java.io.File

object PackageObserver {
    var listener: OnPackageUpdateListener? = null
    private var lastModified = 0L

    fun init(path: String) {
        PeriodicExecutor(5000).execute {
            val spellerFile = File(path)
            if (spellerFile.exists() && lastModified != spellerFile.lastModified()) {
                lastModified = spellerFile.lastModified()
                Timber.d("OnPackage Update!")
                listener?.onPackageUpdate()
            }
        }
    }
}