package no.divvun

import android.content.Context
import no.divvun.domain.Keyboard
import no.divvun.domain.loadKeyboardDescriptor
import no.divvun.pahkat.client.PackageKey
import no.divvun.pahkat.client.PackageKeyParams
import no.divvun.pahkat.client.RepoRecord
import timber.log.Timber

typealias SpellerConfiguration = Map<String, SpellerPackage>
typealias RepoConfiguration = Map<String, RepoRecord>

fun SpellerConfiguration.packageKeys(): List<PackageKey> = values.map { it.packageKey }.toList()
fun SpellerConfiguration.repos(): RepoConfiguration = packageKeys().map { it.repositoryUrl + "/" }.toSet().map { it to RepoRecord(null) }.toMap()

object Spellers {

    private lateinit var internalSpellers: SpellerConfiguration
    val config
        get() = internalSpellers

    operator fun get(languageTag: String): SpellerPackage? {
        return config[languageTag]
    }

    fun init(context: Context) {
        initSpellerConf(context)
    }

    private fun initSpellerConf(context: Context) {
        val jsonFiles = context.assets.list("layouts/").orEmpty()

        internalSpellers = jsonFiles.map {
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

    private fun String.packageKey(): PackageKey {
        val repoUrl = split("/packages")[0]
        Timber.d("repoUrl: $repoUrl")
        val spellerId = split('/').last()
        Timber.d("spellerId: $spellerId")
        return PackageKey(repoUrl, spellerId, PackageKeyParams(platform = "mobile"))
    }
}

data class SpellerPackage(
        val packageKey: PackageKey,
        val spellerFile: String
) {
    fun spellerPath(context: Context): String = "${prefixPath(context)}/pkg/${packageKey.id}/$spellerFile"
}

