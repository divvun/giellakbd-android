package no.divvun

fun createTag(any: Any): String {
    return any.javaClass.simpleName.take(24)
}