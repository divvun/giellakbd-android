package no.divvun

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import arrow.core.Either
import no.divvun.packageobserver.PackageObserver
import no.divvun.pahkat.*
import no.divvun.pahkat.client.*
import no.divvun.pahkat.client.ffi.orThrow
import timber.log.Timber
import java.util.concurrent.TimeUnit


const val spellerFile = "smj.bhfst"
const val packageId = "speller-smj"
const val packageRepoUrl = "https://x.brendan.so/divvun-pahkat-repo"
const val REPO_URL = "https://x.brendan.so/divvun-pahkat-repo/"

fun prefixPath(context: Context): String = "${context.applicationInfo.dataDir}/no_backup/pahkat"
fun spellerPath(context: Context): String = "${prefixPath(context)}/pkg/$packageId/$spellerFile"

class App : Application() {

    override fun onCreate() {
        super.onCreate()

        val tree = Timber.DebugTree()
        Timber.plant(tree)
        Timber.d("onCreate")

        // Init PahkatClient
        PahkatClient.enableLogging()
        PahkatClient.Android.init(applicationInfo.dataDir).orThrow()

        val prefixPath = prefixPath(this)
        initPrefixPackageStore(prefixPath)

        val key = PackageKey(
                packageRepoUrl,
                packageId,
                PackageKeyParams(platform = "mobile")
        )

        PackageObserver.init(this, spellerPath(this))
        ensurePeriodicPackageUpdates(this, prefixPath, key)
    }


    private fun initPrefixPackageStore(prefixPath: String) {
        // Timber.d("Env: ${System.getenv().map { "${it.key}: ${it.value}" }.joinToString(", ")}")

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
}

