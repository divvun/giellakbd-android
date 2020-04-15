package no.divvun.pahkat

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.await
import arrow.core.Either
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import no.divvun.pahkat.client.PackageKey
import no.divvun.pahkat.client.PrefixPackageStore
import no.divvun.pahkat.client.delegate.PackageDownloadDelegate

class DownloadWorker(context: Context, params: WorkerParameters): Worker(context, params) {
    private val packageKeyValue get() = inputData.getString(KEY_PACKAGE_KEY)?.let { PackageKey.from(it) }
    private val packageStorePathValue get() = inputData.getString(KEY_PACKAGE_STORE_PATH)

    private inner class Delegate: PackageDownloadDelegate {
        val coroutineScope = CoroutineScope(Dispatchers.IO)
        override var isDownloadCancelled = false

        override fun onDownloadCancel(packageKey: PackageKey) {
            Log.d("DOWNLOAD", "cancel: $packageKey")
            coroutineScope.launch {
                setProgressAsync(DownloadProgress.Cancelled(packageKey.toString()).toData()).await()
            }
        }

        override fun onDownloadComplete(packageKey: PackageKey, path: String) {
            Log.d("DOWNLOAD", "complete: $packageKey $path")
            coroutineScope.launch {
                setProgressAsync(DownloadProgress.Completed(packageKey.toString(), path).toData()).await()
            }
        }

        override fun onDownloadError(packageKey: PackageKey, error: Exception) {
            Log.e("DOWNLOAD", "error: $packageKey", error)

            coroutineScope.launch {
                Log.d("DOWNLOAD", "OH LAWD HE COMIN'")
                val data = DownloadProgress.Error(packageKey.toString(), error).toData()
                Log.d("DOWNLOAD", "$data")
                setProgressAsync(data).await()
                Log.d("DOWNLOAD", "Finished waiting.")
            }
        }

        override fun onDownloadProgress(packageKey: PackageKey, current: Long, maximum: Long) {
            Log.d("DOWNLOAD", "progress: $packageKey $current/$maximum")

            coroutineScope.launch {
                setProgressAsync(
                        DownloadProgress.Downloading(
                                packageKey.toString(),
                                current,
                                maximum
                        ).toData()
                ).await()
            }
        }
    }

    private val delegate = Delegate()

    override fun onStopped() {
        delegate.isDownloadCancelled = true
        super.onStopped()
    }

    override fun doWork(): Result {
        val packageKey = packageKeyValue ?: return Result.failure(
                IllegalArgumentException("packageKey cannot be null").toData())
        val packageStorePath = packageStorePathValue ?: return Result.failure(
                IllegalArgumentException("packageStorePath cannot be null").toData())

        val packageStore = when (val result = PrefixPackageStore.open(packageStorePath)) {
            is Either.Left -> return Result.failure(result.a.toData())
            is Either.Right -> result.b
        }

        setProgressAsync(DownloadProgress.Starting(packageKey.toString()).toData())

        // This blocks to completion.
        val result = when (val r = packageStore.download(packageKey, delegate)) {
            is Either.Left -> Result.failure(r.a.toData())
            is Either.Right -> Result.success(r.b.toData())
        }

        if (delegate.coroutineScope.isActive) {
            Log.d("DOWNLOAD", "Looping 250ms delay")
            Thread.sleep(250)
        }

        return result
    }
}