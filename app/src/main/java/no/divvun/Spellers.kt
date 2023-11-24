package no.divvun

import android.content.Context
import android.widget.Toast
import io.sentry.Sentry
import no.divvun.domain.Keyboard
import no.divvun.domain.Speller
import no.divvun.domain.loadKeyboardDescriptor
import no.divvun.pahkat.client.PackageKey
import no.divvun.pahkat.client.PackageKeyParams
import no.divvun.pahkat.client.RepoRecord
import timber.log.Timber

data class SpellerConfiguration(val value: Map<String, SpellerPackage>) {
    fun packageKeys(): List<PackageKey> = value.values.map { it.packageKey }.toList()
    fun repos(): RepoConfiguration {
        val map = packageKeys()
                .map { it.repositoryUrl }.toSet()
                .map { it to RepoRecord(null) }
                .toMap()
        return RepoConfiguration(map)
    }
}

data class RepoConfiguration(val value: Map<String, RepoRecord>)


object Spellers {
    var config: SpellerConfiguration = SpellerConfiguration(mapOf())

    operator fun get(languageTag: String): SpellerPackage? {
        return config.value[languageTag]
    }

    fun init(context: Context) {
        initSpellerConf(context)
    }

    private fun initSpellerConf(context: Context) {
        val jsonFiles = context.assets.list("layouts").orEmpty()

        config = SpellerConfiguration(jsonFiles.map {
            Timber.d("Processing Layout: $it")
            val languageTag = it.removeSuffix(".json")
            Timber.d("Loading keyboard descriptor $languageTag")
            val keyboard = loadKeyboardDescriptor(context, languageTag)!!
            Timber.d("Layout loaded: $languageTag with speller: ${keyboard.speller}")
            languageTag to keyboard.spellerPackage { speller ->
                Toast.makeText(context, "Error in: $it = ($speller)", Toast.LENGTH_SHORT).show()
            }
        }.mapNotNull { (languageTag, spellerPackage) ->
            spellerPackage?.let { languageTag to spellerPackage }
        }.toMap())
    }

    private fun Keyboard.spellerPackage(onErrorDialog: (spellerStr: String) -> Unit): SpellerPackage? {
        return speller?.let {
            if (!it.packageUrl.isNullOrEmpty() && !it.path.isNullOrEmpty()) {
                SpellerPackage(it.packageUrl.packageKey(), it.path)
            } else {
                val message =
                    "Exception: Speller missing parameter: (path, packageUrl) = (${it.path}, ${it.packageUrl})"
                Timber.e(message)
                Sentry.captureException(Exception(message))
                onErrorDialog.invoke("${it.path}, ${it.packageUrl}")
                null
            }
        }
    }

    private fun String.packageKey(): PackageKey {
        return PackageKey.from(this)
    }
}

data class SpellerPackage(
        val packageKey: PackageKey,
        val spellerFile: String
) {
    fun spellerPath(context: Context): String = "${prefixPath(context)}/pkg/${packageKey.id}/$spellerFile"
}

