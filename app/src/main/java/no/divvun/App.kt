package no.divvun

import android.app.Application
import android.content.Context
import arrow.core.Either
import no.divvun.packageobserver.PackageObserver
import no.divvun.pahkat.UpdateWorker
import no.divvun.pahkat.client.PahkatClient
import no.divvun.pahkat.client.PrefixPackageStore
import no.divvun.pahkat.client.RepoRecord
import no.divvun.pahkat.client.ffi.orThrow
import timber.log.Timber

fun prefixPath(context: Context): String = "${context.applicationInfo.dataDir}/no_backup/pahkat"

@Suppress("unused")
class App : Application() {

    override fun onCreate() {
        super.onCreate()

        val tree = Timber.DebugTree()
        Timber.plant(tree)
        Timber.d("onCreate")

        // Init PahkatClient
        PahkatClient.enableLogging()
        PahkatClient.Android.init(applicationInfo.dataDir).orThrow()

        Spellers.init(this)

        Timber.d("Spellers ${Spellers.config.values}")

        val prefixPath = prefixPath(this)

        initPrefixPackageStore(prefixPath, Spellers.config.repos())
        PackageObserver.init(this)

        // This can be enable to ensure periodic update is ran on each App start
        //       workManager().cancelUniqueWork(WORKMANAGER_NAME_UPDATE)

        UpdateWorker.ensurePeriodicPackageUpdates(this, prefixPath)
    }

    private fun initPrefixPackageStore(prefixPath: String, repos: Map<String, RepoRecord>) {
        val prefix = when (val result = PrefixPackageStore.openOrCreate(prefixPath)) {
            is Either.Left -> {
                Timber.e("Failed to get packageStore ${result.a}")
                throw RuntimeException("Unable to open/create prefix package store!")
            }
            is Either.Right -> result.b
        }

        // Repos need trailing / or will be very mad.
        val config = prefix.config().orThrow()
        config.setRepos(repos).orThrow()
    }
}

