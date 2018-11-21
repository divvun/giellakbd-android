package no.divvun

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import java.util.*
import java.io.File
import java.io.FileOutputStream

@SuppressLint("StaticFieldLeak")
object DivvunUtils {
    private val tag = javaClass.simpleName!!

    private lateinit var context: Context

    fun initialize(context: Context){
        this.context = context
    }

    fun getSpeller(locale: Locale): DivvunSpell {
        Log.d(tag, "getSpeller() for $locale")
        return getSpeller(resolveSpellerArchive(locale))
    }

    private fun resolveSpellerArchive(locale: Locale): String = "${locale.language}.zhfst"

    private fun getSpeller(fileName: String): DivvunSpell {
        val inputStream = context.resources.assets.open("dicts/$fileName")
        val outputStream = FileOutputStream(File(context.filesDir, fileName))
        inputStream.copyTo(outputStream)
        inputStream.close()
        outputStream.flush()
        outputStream.close()

        return DivvunSpell("${context.filesDir.absolutePath}/$fileName")
    }
}