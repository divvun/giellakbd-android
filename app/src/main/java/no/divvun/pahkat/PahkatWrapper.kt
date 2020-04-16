package no.divvun.pahkat

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.work.*
import arrow.core.Either
import no.divvun.pahkat.client.*
import no.divvun.pahkat.client.ffi.orThrow
import timber.log.Timber
import java.util.concurrent.TimeUnit

class PahkatWrapper(val context: Context, val lifecycle: LifecycleOwner) {
    init {
        val prefixPath = "${context.applicationInfo.dataDir}/no_backup/pahkat"
        initPrefixPackageStore(prefixPath)
        val key = PackageKey(
                "https://x.brendan.so/divvun-pahkat-repo",
                "speller-sms",
                PackageKeyParams(platform = "mobile")
        )

        context.workManager().getWorkInfosForUniqueWorkLiveData(key.workName())
                .observe(lifecycle, Observer {
                    val workEnabled = it.isNotEmpty() && it.first().state != WorkInfo.State.CANCELLED
                    val isRunning = it.isNotEmpty() && it.last().state == WorkInfo.State.RUNNING

                    val progress = it.lastOrNull()?.progress?.into<UpdateProgress>()
                    Timber.d("UpdateProgress: $progress")
                    Timber.d("WorkInfo: $it")
                })

        ensurePeriodicPackageUpdates(context, prefixPath, key)

        //context.workManager().cancelUniqueWork(key.workName())
    }

    private fun ensurePeriodicPackageUpdates(
            context: Context,
            prefixPath: String,
            key: PackageKey
    ): String {
        val req = PeriodicWorkRequestBuilder<UpdateWorker>(1, TimeUnit.HOURS)
                .addTag(WORKMANAGER_TAG_UPDATE)
                .setInputData(key.workData(prefixPath))
                .keepResultsForAtLeast(1, TimeUnit.DAYS)
                .setConstraints(
                        Constraints.Builder()
                                .setRequiredNetworkType(NetworkType.UNMETERED)
                                .setRequiresStorageNotLow(true)
                                .setRequiresBatteryNotLow(true)
                                .build()
                )
                .build()

        val workName = key.workName()
        context.workManager()
                .enqueueUniquePeriodicWork(workName, ExistingPeriodicWorkPolicy.KEEP, req)
        return workName
    }

    private fun initPrefixPackageStore(prefixPath: String) {
        Timber.d("Env: ${System.getenv().map { "${it.key}: ${it.value}" }.joinToString(", ")}")

        val prefix = when (val result = PrefixPackageStore.openOrCreate(prefixPath)) {
            is Either.Left -> {
                Timber.e("Failed to get packageStore ${result.a}")
                throw RuntimeException("Unable to open/create prefix package store!")
            }
            is Either.Right -> result.b
        }

        val config = prefix.config().orThrow()
        config.setRepos(mapOf(REPO_URL to RepoRecord(Repository.Channel.NIGHTLY.value))).orThrow()
    }

}