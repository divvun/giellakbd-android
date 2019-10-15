package no.divvun.divvunspell;

import com.sun.jna.Callback;
import com.sun.jna.Pointer;

interface ErrorCallback extends Callback {
    void invoke(Pointer error);
}
