package no.divvun

import com.sun.jna.*

data class CaseHandlingConfig(
    val startPenalty: Float = 0.0f,
    val endPenalty: Float = 0.0f,
    val midPenalty: Float = 0.0f
)

data class SpellerConfig(
    val nBest: Long?,
    val maxWeight: Float?,
    val beam: Float?,
    val caseHandling: CaseHandlingConfig?,
    val nodePoolSize: Long?
)

class DivvunSpellException(message: String?): Exception(message)

class ThfstChunkedBoxSpeller internal constructor(private val handle: Pointer) {
    @Throws(DivvunSpellException::class)
    fun isCorrect(word: String): Boolean {
        val res = CLibrary.INSTANCE.divvun_thfst_chunked_box_speller_is_correct(handle, word, CLibrary.errorCallback)
        CLibrary.assertNoError()
        return res
    }

    @Throws(DivvunSpellException::class)
    fun suggest(word: String): List<String> {
        val slice = CLibrary.INSTANCE.divvun_thfst_chunked_box_speller_suggest(handle, word, CLibrary.errorCallback)
        CLibrary.assertNoError()
        return suggest(slice)
    }

    @Throws(DivvunSpellException::class)
    fun suggest(word: String, config: SpellerConfig): List<String> {
        val cConfig = CLibrary.CSpellerConfig.from(config)
        val slice = CLibrary.INSTANCE.divvun_thfst_chunked_box_speller_suggest_with_config(handle, word, cConfig, CLibrary.errorCallback)
        CLibrary.assertNoError()
        return suggest(slice)
    }

    @Throws(DivvunSpellException::class)
    private fun suggest(slice: CLibrary.SlicePointer): List<String> {
        val len = CLibrary.INSTANCE.divvun_vec_suggestion_len(slice, CLibrary.errorCallback)
        CLibrary.assertNoError()

        val out = mutableListOf<String>()

        for (i in 0..len.toLong()) {
            val value = CLibrary.INSTANCE.divvun_vec_suggestion_get_value(slice, NativeLong(i, true), CLibrary.errorCallback)
            CLibrary.assertNoError()

            out.add(value.getString(0, "UTF-8"))
        }

        return out
    }
}

class ThfstChunkedBoxSpellerArchive private constructor(private val handle: Pointer) {
    companion object {
        @Throws(DivvunSpellException::class)
        fun open(path: String): ThfstChunkedBoxSpellerArchive {
            val handle = CLibrary.INSTANCE.divvun_thfst_chunked_box_speller_archive_open(path, CLibrary.errorCallback)
            CLibrary.assertNoError()
            return ThfstChunkedBoxSpellerArchive(handle)
        }
    }

    fun speller(): ThfstChunkedBoxSpeller {
        val handle = CLibrary.INSTANCE.divvun_thfst_chunked_box_speller_archive_speller(handle, CLibrary.errorCallback)
        CLibrary.assertNoError()
        return ThfstChunkedBoxSpeller(handle)
    }
}

private interface CLibrary : Library {
    interface ErrorCallback : Callback {
        fun invoke(error: Pointer)
    }

    companion object {
        private var lastError: String? = null

        val errorCallback = object : ErrorCallback {
            override fun invoke(error: Pointer) {
                if (error != Pointer.NULL) {
                    lastError = error.getString(0, "UTF-8")
                }
            }
        }

        @Throws(DivvunSpellException::class)
        fun assertNoError() {
            if (lastError != null) {
                val message = lastError
                lastError = null
                throw DivvunSpellException(message)
            }
        }

        val INSTANCE: CLibrary = Native.loadLibrary("divvunspell", CLibrary::class.java)
    }

    class CCaseHandlingConfig : Structure() {
        companion object {
            fun from(config: CaseHandlingConfig): CCaseHandlingConfig {
                val c = CCaseHandlingConfig()
                c.start_penalty = config.startPenalty
                c.mid_penalty = config.midPenalty
                c.end_penalty = config.endPenalty
                return c
            }
        }

        override fun getFieldOrder() = listOf("n_best", "max_weight", "beam", "case_handling", "node_pool_size")

        var start_penalty: Float = 0.0f
        var end_penalty: Float = 0.0f
        var mid_penalty: Float = 0.0f
    }

    class CSpellerConfig : Structure() {
        companion object {
            fun from(config: SpellerConfig): CSpellerConfig {
                val c = CSpellerConfig()
                c.n_best = NativeLong(config.nBest ?: 0, true)
                c.max_weight = config.maxWeight ?: 0.0f
                c.beam = config.beam ?: 0.0f
                c.case_handling = config.caseHandling?.let { CCaseHandlingConfig.from(it) } ?: CCaseHandlingConfig()
                c.node_pool_size = NativeLong(config.nodePoolSize ?: 256, true)
                return c
            }
        }

        override fun getFieldOrder() = listOf("n_best", "max_weight", "beam", "case_handling", "node_pool_size")

        var n_best = NativeLong(0, true)
        var max_weight = 0.0f
        var beam = 0.0f
        var case_handling = CCaseHandlingConfig()
        var node_pool_size = NativeLong(256, true)
    }

    class SlicePointer : Structure() {
        override fun getFieldOrder() = listOf("data", "len")

        var data: Pointer = Pointer.NULL
        var len: NativeLong? = null
    }

    fun divvun_thfst_chunked_box_speller_archive_open(path: String, error: ErrorCallback): Pointer
    fun divvun_thfst_chunked_box_speller_archive_speller(handle: Pointer, error: ErrorCallback): Pointer
    fun divvun_thfst_chunked_box_speller_is_correct(speller: Pointer, word: String, error: ErrorCallback): Boolean
    fun divvun_thfst_chunked_box_speller_suggest(speller: Pointer, word: String, error: ErrorCallback): SlicePointer
    fun divvun_thfst_chunked_box_speller_suggest_with_config(speller: Pointer, word: String, config: CSpellerConfig, error: ErrorCallback): SlicePointer
    fun divvun_vec_suggestion_len(suggestions: SlicePointer, error: ErrorCallback): NativeLong
    fun divvun_vec_suggestion_get_value(suggestions: SlicePointer, index: NativeLong, error: ErrorCallback): Pointer
}
