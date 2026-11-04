package com.dvnnfrr.saku.receipt

import kotlinx.datetime.LocalDate

/** The day every receipt test treats as today. */
val TODAY = LocalDate(2026, 10, 31)

/** One recognized line per string, stacked top to bottom as a receipt prints them. */
fun linesOf(vararg rows: String): List<OcrLine> = rows.mapIndexed { i, text ->
    OcrLine(text, left = 10, top = i * ROW_PITCH, right = 300, bottom = i * ROW_PITCH + LINE_HEIGHT)
}

fun rowsOf(vararg rows: String): List<ReceiptRow> = receiptRows(linesOf(*rows))

/** Parses the recognizer dump format the fixtures use: `left,top,right,bottom<TAB>text`. */
fun parseDump(text: String): List<OcrLine> = text.lines().mapNotNull { line ->
    val (box, content) = line.split('\t', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
    val (l, t, r, b) = box.split(',').mapNotNull(String::toIntOrNull).takeIf { it.size == 4 } ?: return@mapNotNull null
    OcrLine(content, l, t, r, b)
}

private const val ROW_PITCH = 30
private const val LINE_HEIGHT = 20
