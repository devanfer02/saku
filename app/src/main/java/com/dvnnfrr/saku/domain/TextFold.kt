package com.dvnnfrr.saku.domain

import java.text.Normalizer
import java.util.Locale

private val COMBINING_MARKS = Regex("\\p{Mn}+")
private val WHITESPACE_RUN = Regex("\\s+")

/**
 * Text reduced to what a search compares: "Café  Batavia" becomes "cafe batavia".
 *
 * NFD splits an accented letter into the letter and its combining mark, and dropping
 * the marks is what lets "cafe" find "Café". Lowercasing in [Locale.ROOT] keeps a device
 * set to Turkish from turning "I" into a dotless "ı". `java.text.Normalizer` is plain
 * JVM, so this stays in `domain/`.
 */
fun fold(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
        .lowercase(Locale.ROOT)
        .replace(WHITESPACE_RUN, " ")
        .trim()
