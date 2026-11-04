package com.dvnnfrr.saku.receipt

/**
 * Item names as people read them. Receipts print short upper-case names (`INDOMIE GRG SPCL`);
 * Saku shows and saves `Indomie Goreng Special`. See design.md (receipt-items), "Readable
 * item names" and "Learned words".
 */

/**
 * Words the user taught Saku by editing a name on the Receipt screen. [words] maps a printed
 * token, upper case, to the word it stands for (`BRS` to `Buris`); [names] maps a printed
 * name's [itemKey] to a whole readable name, for an edit that changed the word count.
 */
data class LearnedWords(
    val words: Map<String, String> = emptyMap(),
    val names: Map<String, String> = emptyMap(),
) {
    val isEmpty: Boolean get() = words.isEmpty() && names.isEmpty()

    /** These words with [newer] on top: a later edit of the same token wins. */
    operator fun plus(newer: LearnedWords) = LearnedWords(words + newer.words, names + newer.names)

    companion object {
        val NONE = LearnedWords()
    }
}

/**
 * Abbreviations Indonesian tills print, by whole token. Only expansions that are certain:
 * an unknown token is title-cased, never guessed. Learned words override these.
 */
internal val BUILT_IN_WORDS: Map<String, String> = mapOf(
    "GRG" to "Goreng",
    "SPCL" to "Special",
    "SPC" to "Special",
    "SBN" to "Sabun",
    "SSU" to "Susu",
    "MNYK" to "Minyak",
    "MYK" to "Minyak",
    "BRS" to "Beras",
    "KCP" to "Kecap",
    "SMBL" to "Sambal",
    "SMB" to "Sambal",
    "AYM" to "Ayam",
    "BWG" to "Bawang",
    "TLR" to "Telur",
    "KRPK" to "Kerupuk",
    "KRP" to "Kerupuk",
    "CKLT" to "Cokelat",
    "CKL" to "Cokelat",
    "COKLT" to "Cokelat",
    "STRW" to "Strawberry",
    "STRWB" to "Strawberry",
    "PNDN" to "Pandan",
    "ORI" to "Original",
    "ORG" to "Original",
    "RFL" to "Refill",
    "REF" to "Refill",
    "BTL" to "Botol",
    "KLG" to "Kaleng",
    "BKS" to "Bungkus",
    "SCH" to "Sachet",
    "SCT" to "Sachet",
    "PCH" to "Pouch",
    "DTRJN" to "Deterjen",
    "DTJ" to "Deterjen",
    "PWD" to "Powder",
    "LQD" to "Liquid",
    "MRH" to "Merah",
    "PTH" to "Putih",
    "HJU" to "Hijau",
    "MNS" to "Manis",
    "PDS" to "Pedas",
    "ASN" to "Asin",
    "SDP" to "Sedaap",
    "KCL" to "Kecil",
    "BSR" to "Besar",
    "ISI" to "Isi",
    // Seen in the test receipts (synthetic, CORD and the local real set).
    "MKRONI" to "Makaroni",
    "RMPT" to "Rumput",
    "HZLNT" to "Hazelnut",
    "SHRMP" to "Shrimp",
    "JMB" to "Jumbo",
    // Acronyms that stay upper case.
    "UHT" to "UHT",
)

private val SPACES = Regex("""\s+""")
private val QUANTITY = Regex("""\s+[×xX]\s*(\d+)$""")

/** The name without a trailing quantity, and the quantity (` ×2`) or null. */
private fun splitQuantity(name: String): Pair<String, Int?> {
    val trimmed = name.trim().replace(SPACES, " ")
    val match = QUANTITY.find(trimmed) ?: return trimmed to null
    return trimmed.substring(0, match.range.first) to match.groupValues[1].toInt()
}

private fun tokens(name: String): List<String> = name.split(' ').filter { it.isNotEmpty() }

/**
 * Title case that restarts after a separator, so `G/F` stays `G/F` and `EXT.MINYAK` reads
 * `Ext.Minyak`, but an apostrophe does not (`Hero's`).
 */
private fun titleCase(token: String): String = buildString {
    var start = true
    for (c in token) {
        append(if (start) c.uppercaseChar() else c.lowercaseChar())
        start = !c.isLetter() && c != '\'' && c != '’'
    }
}

/** One token as read: a size or code kept upper case, else learned, built-in, title case. */
private fun readableToken(token: String, learned: LearnedWords): String {
    if (token.any(Char::isDigit)) return token.uppercase()
    val key = token.uppercase()
    return learned.words[key] ?: BUILT_IN_WORDS[key] ?: titleCase(token)
}

/**
 * The name to show and save for a printed item name: `INDOMIE GRG SPCL x2` reads
 * `Indomie Goreng Special ×2`. A whole name the user taught replaces the name first; then
 * each token is expanded by [learned], then [BUILT_IN_WORDS], else title-cased. Sizes and
 * codes (`2L`, `1.8KG`, `6X8GR`) keep their form. Readable names come back unchanged.
 */
fun readableItemName(printed: String, learned: LearnedWords = LearnedWords.NONE): String {
    val (name, quantity) = splitQuantity(printed)
    val alias = learned.names[itemKey(name)]
    val readable = alias ?: tokens(name).joinToString(" ") { readableToken(it, learned) }
    return if (quantity != null) "$readable ×$quantity" else readable
}

/**
 * What an edit teaches. [printed] is the name on the receipt, [draft] what Saku offered and
 * [edited] what the user saved, each without regard to a trailing ` ×n`.
 *
 * With as many words as the printed name, each changed word is learned under its printed
 * token (`BRS` to `Buris`); a token holding a digit (`5KG`) never is. With a different word
 * count the whole name is learned under the printed name's [itemKey].
 */
fun learnedWords(printed: String, draft: String, edited: String): LearnedWords {
    val printedTokens = tokens(splitQuantity(printed).first)
    val draftTokens = tokens(splitQuantity(draft).first)
    val editedName = splitQuantity(edited).first
    val editedTokens = tokens(editedName)
    if (editedTokens.isEmpty() || editedTokens == draftTokens) return LearnedWords.NONE

    if (editedTokens.size != printedTokens.size) {
        return LearnedWords(names = mapOf(itemKey(printedTokens.joinToString(" ")) to editedName))
    }
    val words = printedTokens.indices
        .filter { i -> editedTokens[i] != draftTokens.getOrNull(i) && printedTokens[i].none(Char::isDigit) }
        .associate { i -> printedTokens[i].uppercase() to editedTokens[i] }
    return LearnedWords(words = words)
}
