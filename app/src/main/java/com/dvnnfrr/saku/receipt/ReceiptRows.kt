package com.dvnnfrr.saku.receipt

/** One printed row, rebuilt from the lines on it, left to right. */
data class ReceiptRow(val text: String, val top: Int, val bottom: Int)

/**
 * Rebuilds printed rows from recognized lines.
 *
 * The recognizer returns text in blocks, not in reading order: a label and its amount
 * often arrive far apart (`TOTAL` in one block, `28,000` in another). Lines whose
 * vertical centres sit within 0.6 of a line height belong to the same row.
 */
fun receiptRows(lines: List<OcrLine>): List<ReceiptRow> {
    val rows = mutableListOf<MutableList<OcrLine>>()
    for (line in lines.filter { it.text.isNotBlank() }.sortedBy { it.centreY }) {
        // Measured against the row's first line, not its last, so a slightly slanted
        // photo cannot chain three printed rows into one, line by line.
        val first = rows.lastOrNull()?.first()
        val sameRow = first != null &&
            kotlin.math.abs(line.centreY - first.centreY) < maxOf(line.height, first.height) * ROW_TOLERANCE
        if (sameRow) rows.last().add(line) else rows.add(mutableListOf(line))
    }
    return rows.map { row ->
        ReceiptRow(
            text = row.sortedBy { it.left }.joinToString(" ") { fixDigits(it.text.trim()) },
            top = row.minOf { it.top },
            bottom = row.maxOf { it.bottom },
        )
    }
}

private const val ROW_TOLERANCE = 0.6

/** `O` read for `0` next to a digit or separator: `6O.000`, `25.0O0`. */
private val LETTER_O_IN_NUMBER = Regex("""(?<=[\d.,])[oO](?=[\d.,])|(?<=\d)[oO]\b""")

internal fun fixDigits(text: String): String = text.replace(LETTER_O_IN_NUMBER, "0")
