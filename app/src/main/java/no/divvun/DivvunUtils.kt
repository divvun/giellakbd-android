package no.divvun

import android.annotation.SuppressLint
import android.content.Context
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
        return getSpeller()
    }
    fun getSpeller(): DivvunSpell {
        val inputStream = context.resources.assets.open("se.zhfst")
        val outputStream = FileOutputStream(File(context.filesDir, "se.zhfst"))
        inputStream.copyTo(outputStream)
        inputStream.close()
        outputStream.flush()
        outputStream.close()

        return DivvunSpell("${context.filesDir.absolutePath}/se.zhfst")
    }
}