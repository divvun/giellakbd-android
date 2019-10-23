package no.divvun.divvunspell;

import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;

class CLibrary {
    public static native Pointer divvun_hfst_zip_speller_archive_open(String path, ErrorCallback error);
    public static native Pointer divvun_hfst_zip_speller_archive_speller(Pointer handle, ErrorCallback error);
    public static native boolean divvun_hfst_zip_speller_is_correct(Pointer speller, String word, ErrorCallback error);
    public static native SlicePointer.ByValue divvun_hfst_zip_speller_suggest(Pointer speller, String word, ErrorCallback error);
    public static native Pointer divvun_thfst_chunked_box_speller_archive_open(String path, ErrorCallback error);
    public static native Pointer divvun_thfst_chunked_box_speller_archive_speller(Pointer handle, ErrorCallback error);
    public static native boolean divvun_thfst_chunked_box_speller_is_correct(Pointer speller, String word, ErrorCallback error);
    public static native SlicePointer.ByValue divvun_thfst_chunked_box_speller_suggest(Pointer speller, String word, ErrorCallback error);
    //    fun divvun_thfst_chunked_box_speller_suggest_with_config(speller: Pointer, word: String, config: CSpellerConfig, error: ErrorCallback): SlicePointer
    public static native NativeLong divvun_vec_suggestion_len(SlicePointer.ByValue suggestions, ErrorCallback error);
    public static native Pointer divvun_vec_suggestion_get_value(SlicePointer.ByValue suggestions, NativeLong index, ErrorCallback error);

    static {
        Native.register(CLibrary.class, "divvunspell");
    }
}