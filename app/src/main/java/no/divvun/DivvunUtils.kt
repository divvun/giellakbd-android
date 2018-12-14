package no.divvun

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
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
        try {
            val inputStream = context.resources.assets.open("dicts/$fileName")
            Log.d(TAG, "Outputting file to ${context.filesDir.absolutePath}/$fileName")
            val outputStream = FileOutputStream(File(context.filesDir, fileName))
            inputStream.copyTo(outputStream)
            inputStream.close()
            outputStream.flush()
            outputStream.close()
            Log.d(TAG, "$fileName should now exist in the file system")
        } catch (ex: FileNotFoundException) {
            return null
        }

        return DivvunSpell("${context.filesDir.absolutePath}/$fileName")
    }
}