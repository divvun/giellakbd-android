package no.divvun

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController

fun createTag(any: Any): String {
    return any.javaClass.simpleName.take(24)
}

fun Fragment.navigate(resId: Int, bundle: Bundle? = null, options: NavOptions? = null) {
    findNavController().navigate(resId, bundle, options)
}
