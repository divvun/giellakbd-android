package no.divvun.pahkat

import android.content.Context
import android.util.Log
import androidx.work.Data
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
import no.divvun.pahkat.client.TransactionAction
import no.divvun.pahkat.client.delegate.PackageTransactionDelegate

class PrefixTransactionWorker(context: Context, params: WorkerParameters): Worker(context, params) {
    private val transactionActionsValue get() =
        inputData.getByteArray(KEY_TRANSACTION_ACTIONS)?.let {
            Data.fromByteArray(it).into<List<String>>()?.map { TransactionAction.fromJson<Unit>(it) }
        }
    private val packageStorePathValue get() = inputData.getString(KEY_PACKAGE_STORE_PATH)

    private inner class Delegate: PackageTransactionDelegate {
        val coroutineScope = CoroutineScope(Dispatchers.IO)

        internal var isCancelled = false

        override fun isTransactionCancelled(id: Long): Boolean = isCancelled

        override fun onTransactionCancelled(id: Long) {
            coroutineScope.launch {
                setProgressAsync(TransactionProgress.Cancelled.toData()).await()
            }
        }

        override fun onTransactionCompleted(id: Long) {
            coroutineScope.launch {
                setProgressAsync(TransactionProgress.Completed.toData()).await()
            }
        }

        override fun onTransactionError(
                id: Long,
                packageKey: PackageKey?,
                error: java.lang.Exception?
        ) {
            coroutineScope.launch {
                setProgressAsync(TransactionProgress.Error(packageKey, error).toData()).await()
            }
        }

        override fun onTransactionInstall(id: Long, packageKey: PackageKey?) {
            coroutineScope.launch {
                setProgressAsync(TransactionProgress.Install(packageKey!!).toData()).await()
            }
        }

        override fun onTransactionUninstall(id: Long, packageKey: PackageKey?) {
            coroutineScope.launch {
                setProgressAsync(TransactionProgress.Uninstall(packageKey!!).toData()).await()
            }
        }

        override fun onTransactionUnknownEvent(id: Long, event: Long) {
            coroutineScope.launch {
                setProgressAsync(TransactionProgress.UnknownEvent(event).toData()).await()
            }
        }

    }

    private val delegate = Delegate()

    override fun onStopped() {
        delegate.isCancelled = true
        super.onStopped()
    }

    override fun doWork(): Result {
        val actions = transactionActionsValue ?: return Result.failure(
                IllegalArgumentException("actions cannot be null").toData())
        val packageStorePath = packageStorePathValue ?: return Result.failure(
                IllegalArgumentException("packageStorePath cannot be null").toData())

        val packageStore = when (val result = PrefixPackageStore.open(packageStorePath)) {
            is Either.Left -> return Result.failure(result.a.toData())
            is Either.Right -> result.b
        }

        val tx = when (val result = packageStore.transaction(actions)) {
            is Either.Left -> return Result.failure(result.a.toData())
            is Either.Right -> result.b
        }

//        setProgressAsync(DownloadProgress.Starting(packageKey.toString()).toData())

        // This blocks to completion.
        tx.process(delegate)

        if (delegate.coroutineScope.isActive) {
            Log.d("TRANSACTION", "Looping 250ms delay")
            Thread.sleep(250)
        }

        return Result.success()
    }

}