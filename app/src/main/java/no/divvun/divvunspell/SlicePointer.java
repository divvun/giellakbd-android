package no.divvun.divvunspell;

import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;

@Structure.FieldOrder({ "data", "len" })
public class SlicePointer extends Structure {
    public volatile Pointer data;
    public volatile NativeLong len;

    public static class ByValue extends SlicePointer implements Structure.ByValue {}
}
