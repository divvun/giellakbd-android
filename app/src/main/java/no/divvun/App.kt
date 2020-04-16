package no.divvun

import android.app.Application
import no.divvun.pahkat.client.PahkatClient
import no.divvun.pahkat.client.ffi.orThrow
import timber.log.Timber

class App : Application(){
    override fun onCreate() {
        super.onCreate()

        val tree = Timber.DebugTree()
        Timber.plant(tree)

        // Init PahkatClient
        PahkatClient.enableLogging()
        PahkatClient.Android.init(applicationInfo.dataDir).orThrow()
    }
}