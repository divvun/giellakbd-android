package no.divvun

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import io.sentry.Sentry
import io.sentry.event.Event
import io.sentry.event.EventBuilder
import java.util.*
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream

@SuppressLint("StaticFieldLeak")
object DivvunUtils {
    private val TAG = DivvunUtils::class.java.simpleName

    private lateinit var context: Context

    fun initialize(context: Context){
        this.context = context
    }

    fun getSpeller(locale: Locale?): DivvunSpell? {
        Log.d(TAG, "getSpeller() for $locale")
        if (locale == null) {
            return null
        }
        return getSpeller(resolveSpellerArchive(locale))
    }

    private fun resolveSpellerArchive(locale: Locale): String = "${locale.language}.zhfst"

    private fun getSpeller(fileName: String): DivvunSpell? {
        Log.d(TAG, "Loading dicts/$fileName")
        Sentry.capture(EventBuilder()
                .withMessage("Loading dicts/$fileName")
                .withLevel(Event.Level.DEBUG)
                .build())
        try {
            val inputStream = context.resources.assets.open("dicts/$fileName")
            Sentry.capture(EventBuilder()
                    .withMessage("Outputting file to ${context.filesDir.absolutePath}/$fileName")
                    .withLevel(Event.Level.DEBUG)
                    .build())
            Log.d(TAG, "Outputting file to ${context.filesDir.absolutePath}/$fileName")
            val outputStream = FileOutputStream(File(context.filesDir, fileName))
            inputStream.copyTo(outputStream)
            inputStream.close()
            outputStream.flush()
            outputStream.close()
            Log.d(TAG, "$fileName should now exist in the file system")
            Sentry.capture(EventBuilder()
                    .withMessage("$fileName should now exist in the file system")
                    .withLevel(Event.Level.DEBUG)
                    .build())
        } catch (ex: FileNotFoundException) {
            return null
        }

        return try {
            DivvunSpell("${context.filesDir.absolutePath}/$fileName")
        } catch (ex: Exception) {
            Sentry.capture(ex)
            null
        }
    }
}