package no.divvun.pahkat


import android.content.Context
import android.icu.lang.UCharacter.GraphemeClusterBreak.T
import android.util.Log
import androidx.work.*
import arrow.core.Either
import arrow.core.orNull
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.rxkotlin.Flowables
import no.divvun.pahkat.client.*
import no.divvun.pahkat.client.ffi.orThrow
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.concurrent.TimeUnit

const val TAG_DOWNLOAD = "no.divvun.pahkat.client.DOWNLOAD"
const val TAG_TRANSACTION = "no.divvun.pahkat.client.TRANSACTION"

const val KEY_PACKAGE_KEY = "no.divvun.pahkat.client.packageKey"
const val KEY_TRANSACTION_ACTIONS = "no.divvun.pahkat.client.transactionActions"
const val KEY_PACKAGE_STORE_PATH = "no.divvun.pahkat.client.packageStorePath"
const val KEY_OBJECT = "no.divvun.pahkat.client.object"
const val KEY_OBJECT_TYPE = "no.divvun.pahkat.client.objectType"

fun PackageKey.workName(): String {
    return "download-${this}"
}

fun PackageKey.workData(path: String): Data {
    return Data.Builder()
            .putString(KEY_PACKAGE_KEY, this.toString())
            .putString(KEY_PACKAGE_STORE_PATH, path)
            .build()
}

fun <T> PackageTransaction<T>.workName(): String {
    return "transaction-${this.id}"
}
fun <T> PackageTransaction<T>.workData(path: String, actions: List<TransactionAction<T>>): Data {
    return Data.Builder()
            .putByteArray(KEY_TRANSACTION_ACTIONS, actions.map { it.toString() }.toData().toByteArray())
            .putString(KEY_PACKAGE_STORE_PATH, path)
            .build()
}

inline fun <reified T> T.toData(): Data {
    val baos = ByteArrayOutputStream()
    val oos = ObjectOutputStream(baos)

    oos.writeObject(this)
    oos.flush()

    return Data.Builder()
            .putString(KEY_OBJECT_TYPE, T::class.java.canonicalName)
            .putByteArray(KEY_OBJECT, baos.toByteArray())
            .build()
}

inline fun <reified T> Data.into(): T? {
    val bytes = this.getByteArray(KEY_OBJECT) ?: return null
    val bais = ByteArrayInputStream(bytes)
    val ois = ObjectInputStream(bais)
    return ois.readObject() as? T
}

fun runPackageStoreExample(context: Context) {
    PahkatClient.enableLogging()
    PahkatClient.Android.init(context.applicationInfo.dataDir)

    Log.wtf("ENV", System.getenv().map { "${it.key}: ${it.value}" }.joinToString(", "))
    val prefixPath = "${context.applicationInfo.dataDir}/no_backup/pahkat"

    val result = when (val result = PrefixPackageStore.open(prefixPath)) {
        is Either.Left -> PrefixPackageStore.create(prefixPath)
        is Either.Right -> result
    }

    val prefix = when (result) {
        is Either.Left -> {
            Log.wtf("PREFIX", result.a)
            return
        }
        is Either.Right -> result.b
    }

    val config = prefix.config().orNull() ?: return
    config.setRepos(mapOf("https://x.brendan.so/divvun-pahkat-repo/" to RepoRecord(Repository.Channel.NIGHTLY.value))).orThrow()
    prefix.forceRefreshRepos().orThrow()

    val key = PackageKey("https://x.brendan.so/divvun-pahkat-repo", "speller-sms", PackageKeyParams(platform = "mobile"))

    prefix.downloadInBackground(context, prefixPath, key)

    val subscription = prefix.observeDownloadProgress(context, key)
            .doOnNext { Log.d("Process", it.toString()) }
            .takeUntil { it.isFinished }
            .filter { it.isCompleted }
            .switchMap {
                val tx = when (val r = prefix.transaction(listOf(TransactionAction.install(key, Unit)))) {
                    is Either.Left -> return@switchMap Flowable.error<TransactionProgress>(r.a)
                    is Either.Right -> r.b
                }
                tx.processInBackground(context, prefixPath)
            }
            .subscribe({
                Log.d("TransactionProcess", it.toString())
            }, { Log.wtf("TransactionProcess", it) })
}

fun <T> PackageTransaction<T>.processInBackground(context: Context, packageStorePath: String): Flowable<TransactionProgress> {
    val workManager = WorkManager.getInstance(context)

    val req = OneTimeWorkRequest.Builder(PrefixTransactionWorker::class.java)
            .addTag(TAG_TRANSACTION)
            .setInputData(this.workData(packageStorePath, this.actions))
            .keepResultsForAtLeast(1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .setRequiresStorageNotLow(true)
                    .setRequiresBatteryNotLow(true)
                    .build())
            .build()

    val workName = this.workName()

    workManager
            .beginUniqueWork(workName, ExistingWorkPolicy.KEEP, req)
            .enqueue()

    return observeProgress(context)
}

fun <T> PackageTransaction<T>.observeProgress(context: Context): Flowable<TransactionProgress> {
    val workManager = WorkManager.getInstance(context)
    val liveData = workManager.getWorkInfosForUniqueWorkLiveData(this.workName())

    return Flowables.create(BackpressureStrategy.LATEST) { emitter ->
        val observer: (List<WorkInfo>) -> Unit = {
            val workInfo = it.last()
            val progress = workInfo.progress.into<TransactionProgress>()

            if (progress != null) {
                emitter.onNext(progress)
                if (progress.isFinished) {
                    emitter.onComplete()
                }
            }
        }

        liveData.observeForever(observer)

        emitter.setCancellable {
            liveData.removeObserver(observer)
        }
    }
}

fun PrefixPackageStore.downloadInBackground(context: Context, packageStorePath: String, packageKey: PackageKey): Flowable<DownloadProgress> {
    val workManager = WorkManager.getInstance(context)

    val req = OneTimeWorkRequest.Builder(DownloadWorker::class.java)
            .addTag(TAG_DOWNLOAD)
            .setInputData(packageKey.workData(packageStorePath))
            .keepResultsForAtLeast(1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .setRequiresStorageNotLow(true)
                    .build())
            .build()

    val workName = packageKey.workName()

    workManager
            .beginUniqueWork(workName, ExistingWorkPolicy.KEEP, req)
            .enqueue()

    return observeDownloadProgress(context, packageKey)
}

fun PrefixPackageStore.downloadProgressSync(context: Context, packageKey: PackageKey): DownloadProgress? {
    val workManager = WorkManager.getInstance(context)

    val workInfos = workManager
            .getWorkInfosForUniqueWork(packageKey.workName())
            .get(1, TimeUnit.SECONDS)

    return workInfos.last().progress.into()
}

fun PrefixPackageStore.observeDownloadProgress(context: Context, packageKey: PackageKey): Flowable<DownloadProgress> {
    val workManager = WorkManager.getInstance(context)
    val liveData = workManager.getWorkInfosForUniqueWorkLiveData(packageKey.workName())

    return Flowables.create(BackpressureStrategy.LATEST) { emitter ->
        val observer: (List<WorkInfo>) -> Unit = {
            val workInfo = it.last()
            val progress = workInfo.progress.into<DownloadProgress>()

            if (progress != null) {
                emitter.onNext(progress)
                if (progress.isFinished) {
                    emitter.onComplete()
                }
            }
        }

        liveData.observeForever(observer)

        emitter.setCancellable {
            liveData.removeObserver(observer)
        }
    }
}


