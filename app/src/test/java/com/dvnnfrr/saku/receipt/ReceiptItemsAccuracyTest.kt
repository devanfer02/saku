package com.dvnnfrr.saku.receipt

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The item bars, over the long mock receipts drawn by `data/make-mock-receipts.py`,
 * photographed in overlapping and gapped parts, angled, curled and shadowed, and read by
 * ML Kit on the emulator (`ReceiptAccuracyTest` in androidTest). `items.tsv` holds the
 * items each set's joined photos should give; `sets.tsv` the parts and the gaps.
 *
 * An item is right when its name matches (letters and digits, spaces ignored), its line
 * price is exact and its own discount is exact. The bar is the owner's minimum, 80% on
 * every source (31 Oct 2026). Overlaps must join with no item twice; every gap must be
 * flagged and no overlap may be. The CORD item rate is printed, not held to a bar: its
 * restaurant bills print modifiers, set menus and zero-priced lines the labels treat
 * differently from a till receipt.
 */
class ReceiptItemsAccuracyTest {

    private data class Truth(val name: String, val quantity: Int, val price: Long, val discount: Long)

    private data class Sets(val name: String, val parts: Int, val gaps: List<Int>)

    private fun resource(path: String): String =
        requireNotNull(javaClass.getResource("/receipt/$path")) { "Missing fixture receipt/$path" }.readText()

    private fun tsv(path: String): List<List<String>> = resource(path).lines().drop(1)
        .filter { it.isNotBlank() }
        .map { it.split('\t') }

    private val sets by lazy {
        tsv("long/sets.tsv").map { (name, parts, gaps) ->
            Sets(name, parts.toInt(), if (gaps == "-") emptyList() else gaps.split(',').map(String::toInt))
        }
    }

    private val truth by lazy {
        tsv("long/items.tsv").groupBy({ it[0] }) { (_, name, quantity, price, discount) ->
            Truth(name, quantity.toInt(), price.toLong(), discount.toLong())
        }
    }

    private fun read(set: Sets): ReceiptReading = ReceiptParser.readParts(
        (1..set.parts).map { parseDump(resource("long/${set.name}_p$it.txt")) },
        TODAY,
    )

    private fun key(name: String) = name.uppercase().filter(Char::isLetterOrDigit)

    @Test
    fun `items on the long mock receipts meet the bar`() {
        val report = StringBuilder()
        val failures = mutableListOf<String>()
        val sources = sets.groupBy { it.name.substringBefore('-') }
        for ((source, sourceSets) in sources) {
            var right = 0
            var all = 0
            for (set in sourceSets) {
                val reading = read(set)
                val want = truth.getValue(set.name)
                val left = reading.items.toMutableList()
                val misses = mutableListOf<String>()
                for (item in want) {
                    val hit = left.firstOrNull {
                        key(it.name) == key(item.name) && it.price.rupiah == item.price && it.discount.rupiah == item.discount
                    }
                    if (hit != null) left.remove(hit) else misses += "${item.name} ${item.price}-${item.discount}"
                }
                right += want.size - misses.size
                all += want.size
                report.append("  ${set.name}: ${want.size - misses.size}/${want.size} items, gaps ${reading.gapsAfterPhoto}\n")
                misses.forEach { report.append("    MISS $it\n") }
                left.forEach { report.append("    EXTRA ${it.name} x${it.quantity} ${it.price.rupiah}-${it.discount.rupiah}\n") }

                if (reading.gapsAfterPhoto != set.gaps) {
                    failures += "${set.name}: gaps ${reading.gapsAfterPhoto}, want ${set.gaps}"
                }
                val twice = want.filter { item ->
                    val same = reading.items.filter { key(it.name) == key(item.name) }
                    same.size > 1 || same.any { it.quantity > item.quantity }
                }
                if (twice.isNotEmpty()) failures += "${set.name}: read twice ${twice.map { it.name }}"
            }
            val rate = right * 100 / all
            report.insert(0, "$source: $right/$all items right ($rate%)\n")
            if (rate < ITEM_BAR) failures += "$source: $rate% items right, under the $ITEM_BAR% bar"
        }
        println("Long mock receipts\n$report")
        assertTrue(failures.joinToString("\n") + "\n" + report, failures.isEmpty())
    }

    @Test
    fun `print the item rate on the CORD fixtures`() {
        val want = tsv("cord/items.tsv")
            .map { (file, name, price) -> Triple(file, name, price.toLong()) }
            .filter { it.third >= 100 }
            .groupBy({ it.first }) { it.second to it.third }
            .mapValues { (_, items) -> mergedTruth(items) }
        var right = 0
        var priced = 0
        var all = 0
        for ((file, items) in want) {
            val reading = ReceiptParser.read(parseDump(resource("cord/$file.txt")), TODAY)
            val left = reading.items.toMutableList()
            for ((name, price) in items) {
                all++
                val samePrice = left.firstOrNull { it.price.rupiah == price } ?: continue
                priced++
                if (similarity(key(samePrice.name), key(name)) >= CORD_NAME) right++
                left.remove(samePrice)
            }
        }
        println("CORD fixtures: $all priced items, $right right (${right * 100 / all}%), price found $priced (${priced * 100 / all}%)")
    }

    /** The labels list a repeated line twice; the parser merges them, as design.md section 2 says. */
    private fun mergedTruth(items: List<Pair<String, Long>>): List<Pair<String, Long>> =
        items.groupBy { key(it.first) to it.second }.values.map { same -> same.first().first to same.sumOf { it.second } }

    private companion object {
        const val ITEM_BAR = 80
        const val CORD_NAME = 0.8
    }
}
