package no.divvun.domain

import android.content.Context
import com.android.inputmethod.latin.common.StringUtils
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.reflect.TypeToken
import timber.log.Timber
import java.io.IOException
import java.io.InputStream
import java.lang.reflect.Type
import java.util.*

data class Keyboard(
        val speller: Speller?,
        val deadKeys: Map<String, List<String>>,
        val transforms: DeadKeyNode.Parent
)

data class Speller(
        val path: String,
        val packageUrl: String
)

sealed class DeadKeyNode {
    data class Leaf(val string: String) : DeadKeyNode()
    data class Parent(val children: Map<String, DeadKeyNode>) : DeadKeyNode() {
        constructor(vararg pairs: Pair<String, DeadKeyNode>) : this(mapOf(*pairs))

        fun defaultChild(): Leaf {
            return children[" "] as Leaf
        }
    }
}

class DeadKeyNodeDeserializer : JsonDeserializer<DeadKeyNode> {
    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): DeadKeyNode {

        return when {
            json!!.isJsonObject -> {
                val jsonObject = json.asJsonObject
                val keys = jsonObject.keySet()

                val children = keys.map {
                    it to deserialize(jsonObject.get(it), null, context)
                }.toTypedArray()

                DeadKeyNode.Parent(*children)
            }
            json.isJsonPrimitive -> {
                val raw = json.asString
                val parsedValue = raw.parsedUnicodeString()
                DeadKeyNode.Leaf(parsedValue)
            }
            else -> throw RuntimeException("Unexpected type: $json")
        }
    }

    private val unicodeFormatRegex = """\\u\{[0-9A-Fa-f]+\}""".toRegex()
    private val numberRegex = "[0-9A-Fa-f]+".toRegex()

    private fun String.parsedUnicodeString(): String {
        return unicodeFormatRegex.replace(this) {
            val match = it.value
            val result = numberRegex.find(match, 0)
            StringUtils.newSingleCodePointString(result!!.value.toInt(16))
        }

    }
}

fun loadKeyboardDescriptor(context: Context, locale: Locale): Keyboard? {
    return loadKeyboardDescriptor(context, locale.language)
}

fun loadKeyboardDescriptor(context: Context, language: String): Keyboard? {
    val json: String
    try {
        val inputStream: InputStream = context.assets.open("layouts/$language.json")

        val size = inputStream.available()
        val buffer = ByteArray(size)
        inputStream.read(buffer)
        inputStream.close()
        json = String(buffer, Charsets.UTF_8)
    } catch (e: IOException) {
        e.printStackTrace()
        return null
    }

    val gson = GsonBuilder()
            .registerTypeAdapter(DeadKeyNode.Parent::class.java, DeadKeyNodeDeserializer())
            .create()

    val keyboardType = object : TypeToken<Keyboard>() {}.type!!

    return gson.fromJson(json, keyboardType)
}
