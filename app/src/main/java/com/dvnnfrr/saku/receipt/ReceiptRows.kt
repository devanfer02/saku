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
            text = fixDigits(row.sortedBy { it.left }.joinToString(" ") { it.text.trim() }),
            top = row.minOf { it.top },
            bottom = row.maxOf { it.bottom },
        )
    }
}

private const val ROW_TOLERANCE = 0.6

/** `O` read for `0` next to a digit or separator: `6O.000`, `25.0O0`. */
private val LETTER_O_IN_NUMBER = Regex("""(?<=[\d.,])[oO](?=[\d.,])|(?<=\d)[oO]\b""")

/** A space the recognizer put inside a number: `123. 585`, `54 ,000`, `21, O0`. */
private val SPACE_AFTER_SEPARATOR = Regex("""(\d)\s*([.,])\s+([0-9OoD]{2,3})(?![0-9A-Za-z])""")
private val SPACE_BEFORE_SEPARATOR = Regex("""(\d)\s+([.,])([0-9OoD]{3})(?![0-9A-Za-z])""")

/** A token that is a number with a letter or two misread in it: `6B,000`, `Ii1,000`, `118.D00`. */
private val NUMBERISH = Regex("""^((?:rp\.?)?)([0-9IilOoDB|]+(?:[.,][0-9IilOoDB|]+)*)(\W*)$""", RegexOption.IGNORE_CASE)
private val DIGIT_LOOKALIKES = mapOf('I' to '1', 'i' to '1', 'l' to '1', '|' to '1', 'O' to '0', 'o' to '0', 'D' to '0', 'B' to '8')

/**
 * Undoes the recognizer's usual slips inside numbers, so the amount reader sees `68,000`
 * where the paper says 68,000 and the OCR said `6B,000`. A token is only touched when its
 * real digits outnumber its look-alike letters and it has at least two, so words
 * (`BOX`, `ID`, `Ii`) are left alone.
 */
internal fun fixDigits(text: String): String = text
    .replace(SPACE_AFTER_SEPARATOR, "$1$2$3")
    .replace(SPACE_BEFORE_SEPARATOR, "$1$2$3")
    .split(' ')
    .joinToString(" ") { token ->
        val match = NUMBERISH.find(token) ?: return@joinToString token
        val (prefix, core, suffix) = match.destructured
        val digits = core.count(Char::isDigit)
        val letters = core.count { it in DIGIT_LOOKALIKES }
        if (digits < 2 || letters > digits) token
        else prefix + core.map { DIGIT_LOOKALIKES[it] ?: it }.joinToString("") + suffix
    }
    .replace(LETTER_O_IN_NUMBER, "0")
