package no.divvun

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer

class DivvunSpell(path: String) {
    private interface CLibrary : Library {
        companion object {
            val INSTANCE: CLibrary = Native.loadLibrary("hfstospell", CLibrary::class.java)
        }

        fun speller_archive_new(path: String, error: Pointer?): Pointer
        fun speller_archive_free(handle: Pointer)
        fun speller_meta_get_locale(handle: Pointer): String
    }

    private val handle = CLibrary.INSTANCE.speller_archive_new(path, null)

    val locale: String
        get() = CLibrary.INSTANCE.speller_meta_get_locale(handle)
}