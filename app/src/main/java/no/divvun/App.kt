package no.divvun

import com.android.inputmethod.latin.BuildConfig
import android.app.Application
import android.content.Context
import arrow.core.Either
import com.android.inputmethod.latin.R
import com.bugfender.sdk.Bugfender
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
    companion object {
        private var pahkatHasInitialized = false

        fun ensurePahkatInit(context: Context) {
            if (pahkatHasInitialized) return

            pahkatHasInitialized = true

            Spellers.init(context)
            val prefixPath = prefixPath(context)

            Timber.d("Spellers ${Spellers.config.value.values}")

//            PahkatClient.enableLogging()
            PahkatClient.Android.init(context.applicationInfo.dataDir).orThrow()

            val prefix = when (val result = PrefixPackageStore.openOrCreate(prefixPath)) {
                is Either.Left -> {
                    Timber.e("Failed to get packageStore ${result.a}")
                    throw RuntimeException("Unable to open/create prefix package store!")
                }
                is Either.Right -> result.b
            }

            // Repos need trailing / or will be very mad.
            val config = prefix.config().orThrow()
            config.setRepos(Spellers.config.repos().value).orThrow()
        }
    }

    override fun onCreate() {
        super.onCreate()

        val tree = Timber.DebugTree()
        Timber.plant(tree)
        Timber.d("onCreate")

        // Init PahkatClient

//        ensurePahkatInit(this)
//        PackageObserver.init(this)

//        val bugfenderId = getString(R.string.bugfender_id)
//        if (bugfenderId != "") {
//            Bugfender.init(this, bugfenderId, BuildConfig.DEBUG)
//            Bugfender.enableUIEventLogging(this)
//            Bugfender.enableLogcatLogging()
//        }

        // This is enabled to ensure periodic update is ran on each App start
        // workManager().cancelUniqueWork(WORKMANAGER_NAME_UPDATE)

//        UpdateWorker.ensurePeriodicPackageUpdates(this, prefixPath(this))
    }
}

