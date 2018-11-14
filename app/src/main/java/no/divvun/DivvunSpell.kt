package no.divvun

import com.sun.jna.*
import com.sun.jna.ptr.ByteByReference
import java.lang.IndexOutOfBoundsException

class SpellerInitException(val errorCode: Byte, message: String?): Exception(message)

class DivvunSpell @Throws(SpellerInitException::class) constructor(path: String) {
    private val handle: Pointer

    init {
        val errorCode = ByteByReference(0)
        handle = CLibrary.INSTANCE.speller_archive_new(path, errorCode)

        if (errorCode.value > 0) {
            val errPtr = CLibrary.INSTANCE.speller_get_error(errorCode.value)
            val errString = errPtr.getString(0)
            CLibrary.INSTANCE.speller_str_free(errPtr)
            throw SpellerInitException(errorCode.value, errString)
        }
    }

    val locale: String
        get() = CLibrary.INSTANCE.speller_meta_get_locale(handle)


    fun suggest(word: String, nBest: Long? = null, beam: Float? = null): List<String> {
        return SuggestionList(handle, word, NativeLong(nBest ?: 0), beam ?: 0.0f)
    }

    fun isCorrect(word: String): Boolean {
        return CLibrary.INSTANCE.speller_is_correct(handle, word)
    }

    fun finalize() {
        CLibrary.INSTANCE.speller_archive_free(handle)
    }

    inner class SuggestionList internal constructor(spellerHandle: Pointer, word: String, nBest: NativeLong, beam: Float) : AbstractList<String>() {
        private val handle = CLibrary.INSTANCE.speller_suggest(spellerHandle, word, nBest, beam)
        override val size = CLibrary.INSTANCE.suggest_vec_len(handle).toInt()

        override fun get(index: Int): String {
            if (index < 0 || index >= size) {
                throw IndexOutOfBoundsException()
            }

            val rawValue = CLibrary.INSTANCE.suggest_vec_get_value(handle, NativeLong(index.toLong()))
            val string = rawValue.getString(0)
            CLibrary.INSTANCE.suggest_vec_value_free(rawValue)
            return string
        }

        fun finalize() {
            CLibrary.INSTANCE.suggest_vec_free(handle)
        }
    }
    
    private interface CLibrary : Library {
        companion object {
            val INSTANCE: CLibrary = Native.loadLibrary("hfstospell", CLibrary::class.java)
        }

        class token_record_t : Structure() {
            override fun getFieldOrder() = listOf("type", "start", "end", "value")

            var type: Byte = 0
            var start: NativeLong? = null
            var end: NativeLong? = null
            var value: String? = null

            class ByReference : Structure.ByReference
        }

        fun speller_archive_new(path: String, error: ByteByReference): Pointer
        fun speller_get_error(code: Byte): Pointer
        fun speller_archive_free(handle: Pointer)
        fun speller_str_free(string: Pointer)
        fun speller_meta_get_locale(handle: Pointer): String
        fun speller_suggest(handle: Pointer, word: String, n_best: NativeLong, beam: Float): Pointer
        fun speller_is_correct(handle: Pointer, word: String): Boolean
        fun suggest_vec_free(handle: Pointer)
        fun suggest_vec_len(handle: Pointer): NativeLong
        fun suggest_vec_get_value(handle: Pointer, index: NativeLong): Pointer
        fun suggest_vec_get_weight(handle: Pointer, index: NativeLong): Float
        fun suggest_vec_value_free(value: Pointer)
        fun speller_tokenize(string: String): Pointer
        fun speller_token_next(handle: Pointer, record: token_record_t.ByReference): Boolean
        fun speller_tokenizer_free(handle: Pointer)
    }

}