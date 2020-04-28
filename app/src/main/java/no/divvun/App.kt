package no.divvun

import android.app.Application
import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import arrow.core.Either
import no.divvun.packageobserver.PackageObserver
import no.divvun.pahkat.UpdateWorker
import no.divvun.pahkat.WORKMANAGER_TAG_UPDATE
import no.divvun.pahkat.client.*
import no.divvun.pahkat.client.ffi.orThrow
import no.divvun.pahkat.workManager
import timber.log.Timber
import java.net.URI
import java.util.concurrent.TimeUnit

const val WM_SPELLERUPDATEWORKER_NAME = "SPELLER_UPDATE_WORKER"

fun prefixPath(context: Context): String = "${context.applicationInfo.dataDir}/no_backup/pahkat"

val spellers = mapOf(
        "smj_NO" to SpellerPackage("https://x.brendan.so/divvun-pahkat-repo/speller-smj".packageKey(), "smj.bhfst"),
        "smj_SE" to SpellerPackage("https://x.brendan.so/divvun-pahkat-repo/speller-smj".packageKey(), "smj.bhfst")
)

fun String.packageKey(): PackageKey {
    val url = URI.create(this).toURL()
    val repoUrl = "${url.protocol}://${url.authority}"
    val spellerId = url.file
    return PackageKey(repoUrl, spellerId, PackageKeyParams(platform = "mobile"))
}

data class SpellerPackage(
        val packageKey: PackageKey,
        val spellerFile: String
) {
    fun spellerPath(context: Context): String = "${prefixPath(context)}/pkg/${packageKey.id}/$spellerFile"
}

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
        initPrefixPackageStore(prefixPath, spellers.values.map { it.packageKey }.toList())

        PackageObserver.init(this)

        workManager().cancelUniqueWork(WM_SPELLERUPDATEWORKER_NAME)
        ensurePeriodicPackageUpdates(this, prefixPath)
    }


    private fun initPrefixPackageStore(prefixPath: String, packages: List<PackageKey>) {
        // Timber.d("Env: ${System.getenv().map { "${it.key}: ${it.value}" }.joinToString(", ")}")

        val prefix = when (val result = PrefixPackageStore.openOrCreate(prefixPath)) {
            is Either.Left -> {
                Timber.e("Failed to get packageStore ${result.a}")
                throw RuntimeException("Unable to open/create prefix package store!")
            }
            is Either.Right -> result.b
        }

        val repos = packages.map { it.repositoryUrl }.toSet().map { it to RepoRecord(null) }.toMap()
        val config = prefix.config().orThrow()
        config.setRepos(repos).orThrow()
    }

    private fun ensurePeriodicPackageUpdates(
            context: Context,
            prefixPath: String
    ): String {
        val req = PeriodicWorkRequestBuilder<UpdateWorker>(1, TimeUnit.DAYS)
                .addTag(WORKMANAGER_TAG_UPDATE)
                .setConstraints(
                        Constraints.Builder()
                                .setRequiredNetworkType(NetworkType.UNMETERED)
                                .setRequiresStorageNotLow(true)
                                .setRequiresBatteryNotLow(true)
                                .build()
                )
                .build()

        context.workManager()
                .enqueueUniquePeriodicWork(WM_SPELLERUPDATEWORKER_NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        return prefixPath
    }
}

