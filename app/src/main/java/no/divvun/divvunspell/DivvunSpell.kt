package no.divvun.divvunspell

import com.sun.jna.*

data class CaseHandlingConfig(
    val startPenalty: Float = 0.0f,
    val endPenalty: Float = 0.0f,
    val midPenalty: Float = 0.0f
)

data class SpellerConfig(
    val nBest: Long? = null,
    val maxWeight: Float? = null,
    val beam: Float? = null,
    val caseHandling: CaseHandlingConfig? = null,
    val nodePoolSize: Long? = null
)

class DivvunSpellException(message: String?): Exception(message)

class ThfstChunkedBoxSpeller internal constructor(private val handle: Pointer) {
    @Throws(DivvunSpellException::class)
    fun isCorrect(word: String): Boolean {
        val res = CLibrary.divvun_thfst_chunked_box_speller_is_correct(handle, word, errorCallback)
        assertNoError()
        return res
    }

    @Throws(DivvunSpellException::class)
    fun suggest(word: String): List<String> {
        val slice = CLibrary.divvun_thfst_chunked_box_speller_suggest(handle, word, errorCallback)
        println("MMM " + slice)
        assertNoError()
        return suggest(slice)
    }

    @Throws(DivvunSpellException::class)
    fun suggest(word: String, config: SpellerConfig): List<String> {
//        val cConfig = se.brendan.divvunspell.CSpellerConfig.from(config)
//        val slice = se.brendan.divvunspell.CLibrary.divvun_thfst_chunked_box_speller_suggest_with_config(handle, word, cConfig, se.brendan.divvunspell.errorCallback)

        val slice = CLibrary.divvun_thfst_chunked_box_speller_suggest(handle, word, errorCallback)
        assertNoError()
        return suggest(slice)
    }

    @Throws(DivvunSpellException::class)
    private fun suggest(slice: SlicePointer.ByValue): List<String> {
        val len = CLibrary.divvun_vec_suggestion_len(slice, errorCallback)
        assertNoError()

        val out = mutableListOf<String>()

        for (i in 0L until len.toLong()) {
            val value = CLibrary.divvun_vec_suggestion_get_value(slice, NativeLong(i, true), errorCallback)
            assertNoError()
            out.add(value.getString(0, "UTF-8"))
        }

        return out
    }
}

class ThfstChunkedBoxSpellerArchive private constructor(private val handle: Pointer) {
    companion object {
        @Throws(DivvunSpellException::class)
        fun open(path: String): ThfstChunkedBoxSpellerArchive {
            val handle = CLibrary.divvun_thfst_chunked_box_speller_archive_open(path, errorCallback)
            assertNoError()
            return ThfstChunkedBoxSpellerArchive(handle)
        }
    }

    fun speller(): ThfstChunkedBoxSpeller {
        val spellerHandle = CLibrary.divvun_thfst_chunked_box_speller_archive_speller(handle, errorCallback)
        assertNoError()
        return ThfstChunkedBoxSpeller(spellerHandle)
    }
}

private var lastError: String? = null

private val errorCallback = ErrorCallback { error ->
    lastError = error.getString(0, "UTF-8")
}

@Throws(DivvunSpellException::class)
private fun assertNoError() {
    if (lastError != null) {
        val message = lastError
        lastError = null
        throw DivvunSpellException(message)
    }
}