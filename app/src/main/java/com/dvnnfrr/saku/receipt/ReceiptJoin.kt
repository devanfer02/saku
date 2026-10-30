package com.dvnnfrr.saku.receipt

/**
 * The printed rows of several photos of one receipt, taken top to bottom, as one list.
 * [gapsAfterPhoto] holds `n` when photos `n` and `n + 1` (counting from 1) share no row,
 * so a part of the receipt may be missing between them.
 */
data class JoinedRows(val rows: List<ReceiptRow>, val gapsAfterPhoto: List<Int>)

private const val WINDOW = 8
private const val SIMILAR = 0.85
private const val MATCH_SHARE = 0.6
private const val MAX_SKIP = 1
private const val PHOTO_GAP = 40
private const val MIN_KEY = 4

/**
 * Joins photos' rows. Where the end of one photo and the start of the next show the same
 * rows, the next photo's copy is dropped; where they share none, the rows are appended
 * and a gap is reported. Rows printed twice inside one photo are both kept.
 *
 * Two rows match when their letters and digits are at least 85% alike and their amounts,
 * if both have one, agree. An overlap needs at least one matching row with an amount, so
 * a repeated label alone never joins two photos. See design.md (receipt-items) section 2.
 */
fun joinPhotos(photos: List<List<ReceiptRow>>): JoinedRows {
    val joined = mutableListOf<ReceiptRow>()
    val gaps = mutableListOf<Int>()
    photos.forEachIndexed { index, rows ->
        if (index == 0) {
            joined += rows
            return@forEachIndexed
        }
        val fit = overlap(joined, rows)
        if (fit == null) {
            gaps += index
            joined += shifted(rows, below = joined.lastOrNull())
            return@forEachIndexed
        }
        // Each row in the overlap is kept from the photo that read it better: a row at
        // the edge of a photo is often cut and misread in that photo.
        val pairs = minOf(joined.size - fit.first, rows.size - fit.second)
        val bridge = (0 until pairs).map { j ->
            val above = joined[fit.first + j]
            val below = rows[fit.second + j]
            val better = same(above, below) && rowQuality(above.text) > rowQuality(below.text)
            if (better) below.copy(text = above.text) else below
        }
        while (joined.size > fit.first) joined.removeAt(joined.lastIndex)
        joined += shifted(bridge + rows.drop(fit.second + pairs), below = joined.lastOrNull())
    }
    return JoinedRows(joined, gaps)
}

/** Where to cut: keep `above[0 until first]`, then `below` from `second` on. */
private fun overlap(above: List<ReceiptRow>, below: List<ReceiptRow>): Pair<Int, Int>? {
    if (above.isEmpty() || below.isEmpty()) return null
    data class Fit(val start: Int, val skip: Int, val matches: Int)

    val fits = mutableListOf<Fit>()
    for (start in maxOf(0, above.size - WINDOW) until above.size) {
        for (skip in 0..MAX_SKIP) {
            val pairs = minOf(above.size - start, below.size - skip, WINDOW)
            if (pairs <= 0) continue
            val matched = (0 until pairs).filter { same(above[start + it], below[skip + it]) }
            val anchored = matched.any { hasAmount(above[start + it]) && hasAmount(below[skip + it]) }
            if (anchored && matched.size >= pairs * MATCH_SHARE) fits += Fit(start, skip, matched.size)
        }
    }
    val best = fits.maxWithOrNull(compareBy<Fit> { it.matches }.thenByDescending { it.skip }) ?: return null
    return best.start to best.skip
}

/*
 * When both rows carry an amount and the amounts agree, their letters decide: the digits
 * around a price are where a photo's edge cuts and misreads most (`27.80A`). Two photos
 * may also group a two-row item differently (the price on the name row in one, on the
 * quantity row in the other), so a row with no amount is compared without amounts.
 */
internal fun same(a: ReceiptRow, b: ReceiptRow): Boolean {
    val amountA = amountsIn(a.text).lastOrNull()
    val amountB = amountsIn(b.text).lastOrNull()
    if (amountA != null && amountB != null) {
        if (!lookAlike(amountA.rupiah, amountB.rupiah)) return false
        return similarity(a.text.filter(Char::isLetter).uppercase(), b.text.filter(Char::isLetter).uppercase()) >= SIMILAR
    }
    val keyA = joinKey(withoutAmounts(a.text))
    val keyB = joinKey(withoutAmounts(b.text))
    return keyA.length >= MIN_KEY && similarity(keyA, keyB) >= SIMILAR
}

private fun withoutAmounts(text: String): String = text.replace(AMOUNT_IN_ROW, " ")

private val AMOUNT_IN_ROW = Regex("""(?<![\d.,])-?\(?(?:rp\.?\s*)?\d{1,3}(?:[.,]\d{3})+\)?|(?<![\d.,])\d{4,7}(?![\d.,])""", RegexOption.IGNORE_CASE)

private fun hasAmount(row: ReceiptRow): Boolean = amountsIn(row.text).isNotEmpty()

private fun joinKey(text: String): String = text.uppercase().filter { it.isLetterOrDigit() }

private fun shifted(rows: List<ReceiptRow>, below: ReceiptRow?): List<ReceiptRow> {
    if (below == null || rows.isEmpty()) return rows
    val offset = below.bottom + PHOTO_GAP - rows.first().top
    return rows.map { it.copy(top = it.top + offset, bottom = it.bottom + offset) }
}

/** How alike two strings are, from 0 to 1, by edit distance. */
internal fun similarity(a: String, b: String): Double {
    if (a.isEmpty() && b.isEmpty()) return 1.0
    var previous = IntArray(b.length + 1) { it }
    for (i in 1..a.length) {
        val current = IntArray(b.length + 1)
        current[0] = i
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
        }
        previous = current
    }
    return 1.0 - previous[b.length].toDouble() / maxOf(a.length, b.length)
}
