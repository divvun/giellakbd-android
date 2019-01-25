package no.divvun

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.android.inputmethod.latin.BuildConfig
import io.sentry.Sentry
import io.sentry.event.Event
import io.sentry.event.EventBuilder
import java.util.*
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream

//@SuppressLint("StaticFieldLeak")
object DivvunUtils {
    private val TAG = DivvunUtils::class.java.simpleName

    private fun dictFileName(locale: Locale) = "${locale.language}.zhfst"
    private fun cachedDictFileName(locale: Locale) = "${locale.language}_v${BuildConfig.VERSION_NAME}.zhfst"

    private fun clearOldDicts(context: Context, locale: Locale) {
        val filesDir = File(context.filesDir.absolutePath)

        val oldDicts = filesDir.listFiles { path ->
            path.startsWith("${locale.language}_v") &&
                !path.endsWith("_v${BuildConfig.VERSION_NAME}.zhfst") }
        if (oldDicts.isNotEmpty()) {
            oldDicts.forEach {
                try {
                    it.delete()
                } catch (ex: Exception) {
                    // Do nothing.
                }
            }
        }
    }

    private fun hasDictInAssets(context: Context, locale: Locale): Boolean {
        return try {
            context.resources.assets.open("dicts/${dictFileName(locale)}")
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun writeDict(context: Context, locale: Locale) {
        val inputStream = context.resources.assets.open("dicts/${dictFileName(locale)}")
        Log.d(TAG, "Outputting file to ${context.filesDir.absolutePath}/${dictFileName(locale)}")
        val outputStream = FileOutputStream(File(context.filesDir, cachedDictFileName(locale)))
        inputStream.copyTo(outputStream)
        inputStream.close()
        outputStream.flush()
        outputStream.close()
    }

    private fun ensureCached(context: Context, locale: Locale) {
        // Check for anything prefixed with this language and delete it if it's not the correct version

        clearOldDicts(context, locale)
        writeDict(context, locale)
    }

    fun getSpeller(context: Context, locale: Locale?): DivvunSpell? {
        Log.d(TAG, "getSpeller() for $locale")

        // We do not trust Java to provide us this non-null.
        if (locale == null || !hasDictInAssets(context, locale)) {
            return null
        }

        try {
            ensureCached(context, locale)
        } catch (ex: Exception) {
            Sentry.capture(ex)
            return null
        }

        return try {
            DivvunSpell("${context.filesDir.absolutePath}/${cachedDictFileName(locale)}")
        } catch (ex: Exception) {
            Sentry.capture(ex)
            null
        }
    }
}