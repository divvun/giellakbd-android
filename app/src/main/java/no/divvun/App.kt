package no.divvun

import android.app.Application
import android.content.Context
import androidx.work.Data
import arrow.core.Either
import no.divvun.packageobserver.PackageObserver
import no.divvun.pahkat.KEY_PACKAGE_STORE_PATH
import no.divvun.pahkat.UpdateWorker
import no.divvun.pahkat.WORKMANAGER_NAME_UPDATE
import no.divvun.pahkat.client.*
import no.divvun.pahkat.client.ffi.orThrow
import no.divvun.pahkat.workManager
import timber.log.Timber
import java.net.URI

fun prefixPath(context: Context): String = "${context.applicationInfo.dataDir}/no_backup/pahkat"


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

        initPrefixPackageStore(prefixPath, Spellers.config.repos() )
        PackageObserver.init(this)

        // This can be enable to ensure periodic update is ran on each App start
 //       workManager().cancelUniqueWork(WORKMANAGER_NAME_UPDATE)

        UpdateWorker.ensurePeriodicPackageUpdates(this, prefixPath)
    }

    private fun initPrefixPackageStore(prefixPath: String, repos: Map<String, RepoRecord>) {
        // Timber.d("Env: ${System.getenv().map { "${it.key}: ${it.value}" }.joinToString(", ")}")

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

