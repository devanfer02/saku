package com.dvnnfrr.saku.receipt

/** The store or merchant name, and the row it was on. */
data class StorePick(val name: String, val rowIndex: Int)

/**
 * Chains common in Indonesia, keyed by a form that survives OCR slips, with the name as
 * it should read in a note. A short list on purpose: it only fixes spelling for names the
 * recognizer often mangles (`lNDOMARET`); any other store still comes from the top row.
 */
private val CHAINS = listOf(
    "indomaret" to "Indomaret",
    "alfamidi" to "Alfamidi",
    "alfamart" to "Alfamart",
    "superindo" to "Superindo",
    "lawson" to "Lawson",
    "familymart" to "FamilyMart",
    "solaria" to "Solaria",
    "waroengsteak" to "Waroeng Steak & Shake",
    "kfc" to "KFC",
    "mcdonald" to "McDonald's",
    "starbucks" to "Starbucks",
    "janjijiwa" to "Janji Jiwa",
    "kopikenangan" to "Kopi Kenangan",
    "gacoan" to "Mie Gacoan",
    "hokben" to "HokBen",
)

/** Screenshot labels whose text, on the same row or the next, names who was paid. */
private val PAYEE = Regex(
    """^\s*(pembayaran\s*ke|bayar\s*ke|nama\s*merchant|merchant|nama\s*penerima|penerima)\b[:\s]*(.*)$""",
    RegexOption.IGNORE_CASE,
)

private val LETTERS = Regex("""\p{L}{3,}""")

/** A status-bar or clock row: `08:16`, `13:54 k`. Never a store. */
private val CLOCK = Regex("""^\s*\d{1,2}[:.]\d{2}\b""")

/**
 * The store, by the first rule that answers:
 * 1. a known chain anywhere in the top quarter of the image;
 * 2. a payee label on a payment screenshot (`Pembayaran ke`, `Merchant`, `Penerima`);
 * 3. the first top-quarter row with a word of three letters or more and no amount.
 */
fun pickStore(rows: List<ReceiptRow>): StorePick? {
    if (rows.isEmpty()) return null
    val top = rows.minOf { it.top }
    val quarter = top + (rows.maxOf { it.bottom } - top) / 4
    val head = rows.withIndex().filter { it.value.top <= quarter }

    head.firstNotNullOfOrNull { (index, row) ->
        val key = chainKey(row.text)
        CHAINS.firstOrNull { (k, _) -> k in key }?.let { StorePick(it.second, index) }
    }?.let { return it }

    rows.forEachIndexed { index, row ->
        val match = PAYEE.find(row.text) ?: return@forEachIndexed
        val sameRow = match.groupValues[2].trim()
        if (sameRow.isNameLike()) return StorePick(sameRow, index)
        val next = rows.getOrNull(index + 1)?.text?.trim()
        if (next != null && next.isNameLike()) return StorePick(next, index + 1)
    }

    return head.firstOrNull { (_, row) -> row.text.isNameLike() && !CLOCK.containsMatchIn(row.text) }
        ?.let { (index, row) -> StorePick(row.text.trim(), index) }
}

private fun String.isNameLike(): Boolean =
    LETTERS.containsMatchIn(this) && amountsIn(this).isEmpty() && !isPaymentRow(this)

/** Lower case, letters only, with the usual OCR swaps undone: `lNDOMARET` to `indomaret`. */
private fun chainKey(text: String): String = text.lowercase()
    .replace('0', 'o').replace('1', 'i').replace('|', 'i')
    .replace(Regex("""\bl(?=nd)"""), "i")
    .filter { it in 'a'..'z' }
