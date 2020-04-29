package no.divvun

import android.app.Application
import android.content.Context
import androidx.work.Data
import arrow.core.Either
import no.divvun.domain.Keyboard
import no.divvun.domain.loadKeyboardDescriptor
import no.divvun.packageobserver.PackageObserver
import no.divvun.pahkat.KEY_PACKAGE_STORE_PATH
import no.divvun.pahkat.UpdateWorker
import no.divvun.pahkat.WORKMANAGER_NAME_UPDATE
import no.divvun.pahkat.client.*
import no.divvun.pahkat.client.ffi.orThrow
import no.divvun.pahkat.workManager
import timber.log.Timber
import java.net.URI

lateinit var spellers: Map<String, SpellerPackage>

fun prefixPath(context: Context): String = "${context.applicationInfo.dataDir}/no_backup/pahkat"

fun String.packageKey(): PackageKey {
    val url = URI.create(this).toURL()
    Timber.d("url: $url")
    val repoUrl = "https://x.brendan.so/divvun-pahkat-repo"
    Timber.d("repoUrl: $repoUrl")
    val spellerId = split('/').last()
    Timber.d("spellerId: $spellerId")
    return PackageKey(repoUrl, spellerId, PackageKeyParams(platform = "mobile"))
}

fun String.workData(): Data {
    return Data.Builder()
            .putString(KEY_PACKAGE_STORE_PATH, this)
            .build()
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

        initSpellerConf(this)

        Timber.d("Spellers ${spellers.values}")

        val prefixPath = prefixPath(this)
        initPrefixPackageStore(prefixPath, spellers.values.map { it.packageKey }.toList())

        PackageObserver.init(this)

        workManager().cancelUniqueWork(WORKMANAGER_NAME_UPDATE)
        UpdateWorker.ensurePeriodicPackageUpdates(this, prefixPath)
    }

    private fun initSpellerConf(context: Context) {
        val jsonFiles = context.assets.list("layouts/").orEmpty()

        spellers = jsonFiles.map {
            Timber.d("Processing Layout: $it")
            val languageTag = it.removeSuffix(".json")
            Timber.d("Loading keyboard descriptor $languageTag")
            val keyboard = loadKeyboardDescriptor(context, languageTag)!!
            Timber.d("Layout loaded: $languageTag with speller: ${keyboard.speller}")
            languageTag to keyboard.spellerPackage()
        }.mapNotNull { (languageTag, spellerPackage) ->
            spellerPackage?.let { languageTag to spellerPackage }
        }.toMap()
    }

    private fun Keyboard.spellerPackage(): SpellerPackage? {
        return speller?.let {
            SpellerPackage(it.packageUrl.packageKey(), speller.path)
        }
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

        // Repos need trailing / or will be very mad.
        val repos = packages.map { it.repositoryUrl + "/" }.toSet().map { it to RepoRecord(null) }.toMap()
        val config = prefix.config().orThrow()
        config.setRepos(repos).orThrow()
    }

}

