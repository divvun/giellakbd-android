package no.divvun

import java.util.*

/**
 * Because Android likes strings as locale.
 * https://softwareengineering.stackexchange.com/questions/325458/parse-both-en-us-and-en-us-as-locale-in-java
 */
fun String.toLocale(): Locale {
    return Locale.forLanguageTag(replace('_', '-'))
}

fun String.toLanguageTag(): String {
    return toLocale().toLanguageTag()
}
