package com.dvnnfrr.saku.receipt

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Item accuracy over the local set of real web photos of long receipts, which must never be
 * committed (they are not ours to publish).
 *
 * Skipped unless `RECEIPT_LONG_LOCAL_SET` names a folder holding recognizer dumps written by
 * `ReceiptAccuracyTest` on the emulator and an `items-truth.tsv` (`file`, `item_name`,
 * `line_price_idr`, `discount_idr`). For each photo `<name>.jpg` the folder may hold:
 *
 * - `<name>.txt`: the whole photo, read as one part;
 * - `<name>-ov_a.txt`, `<name>-ov_b.txt`: two parts overlapping by about 15%;
 * - `<name>-gap_a.txt`, `<name>-gap_b.txt`: two parts with about 12% missing between them.
 *
 * An item is right when its line price is exact and its name is at least 80% alike
 * (letters and digits), the same name rule as the CORD rate. Whole photos and overlap sets
 * count toward the rate per source (the store, from the file name's prefix); a gap set has
 * items missing on purpose, so it counts only whether the gap is flagged. An overlap set
 * must keep every item once. Reports and never fails: the bar is checked by reading the
 * report, as with `LocalReceiptAccuracyTest`.
 */
class LocalReceiptItemsTest {

    private data class Truth(val name: String, val price: Long)

    private fun key(name: String) = name.uppercase().filter(Char::isLetterOrDigit)

    @Test
    fun `report item accuracy on the local long-receipt set`() {
        val dir = System.getenv("RECEIPT_LONG_LOCAL_SET")?.let(::File)?.takeIf { it.isDirectory }
        assumeTrue("RECEIPT_LONG_LOCAL_SET not set; local item accuracy skipped", dir != null)
        dir!!

        val truth = File(dir, "items-truth.tsv").readLines().drop(1).filter { it.isNotBlank() }
            .map { it.split('\t') }
            .groupBy({ it[0].substringBeforeLast('.') }) { Truth(it[1], it[2].toLong()) }

        fun dump(name: String): List<OcrLine>? = File(dir, "$name.txt").takeIf { it.isFile }?.let { parseDump(it.readText()) }

        // `loose` is a second rate printed beside the strict one, never instead of it: the
        // price exact and the name only half alike, to tell misread names from wrong prices.
        data class Score(var right: Int = 0, var loose: Int = 0, var all: Int = 0)
        val bySource = sortedMapOf<String, Score>()
        val report = StringBuilder()

        fun score(label: String, source: String, reading: ReceiptReading, want: List<Truth>) {
            val left = reading.items.toMutableList()
            val misses = mutableListOf<Truth>()
            for (item in want) {
                val hit = left.firstOrNull { it.price.rupiah == item.price && similarity(key(it.name), key(item.name)) >= NAME }
                if (hit != null) left.remove(hit) else misses += item
            }
            val right = want.size - misses.size
            val looseLeft = reading.items.toMutableList()
            val loose = want.count { item ->
                val hit = looseLeft.firstOrNull { it.price.rupiah == item.price && similarity(key(it.name), key(item.name)) >= LOOSE_NAME }
                hit?.let(looseLeft::remove) != null
            }
            bySource.getOrPut(source) { Score() }.apply { this.right += right; this.loose += loose; all += want.size }
            report.append("  $label: $right/${want.size} items, gaps ${reading.gapsAfterPhoto}\n")
            misses.forEach { report.append("    MISS ${it.name} ${it.price}\n") }
            left.forEach { report.append("    EXTRA ${it.name} x${it.quantity} ${it.price.rupiah}-${it.discount.rupiah}\n") }
        }

        var overlaps = 0
        var overlapsClean = 0
        var gaps = 0
        var gapsFlagged = 0
        for ((name, want) in truth) {
            val source = name.substringBefore('_')
            dump(name)?.let { score(name, source, ReceiptParser.readParts(listOf(it), TODAY), want) }
            if (System.getenv("RECEIPT_LONG_ROWS") != null) {
                for (part in listOf(name, "$name-ov_a", "$name-ov_b")) {
                    dump(part)?.let { lines -> report.append("  ROWS $part\n" + receiptRows(lines).joinToString("\n") { "    | ${it.text}" } + "\n") }
                }
            }

            val ov = listOfNotNull(dump("$name-ov_a"), dump("$name-ov_b")).takeIf { it.size == 2 }
            if (ov != null) {
                val reading = ReceiptParser.readParts(ov, TODAY)
                score("$name-ov", source, reading, want)
                overlaps++
                // A read item left over after matching that still looks like a truth item
                // was joined twice.
                val left = reading.items.toMutableList()
                for (item in want) {
                    left.firstOrNull { it.price.rupiah == item.price && similarity(key(it.name), key(item.name)) >= NAME }
                        ?.let(left::remove)
                }
                val twice = left.filter { read ->
                    want.any { read.price.rupiah == it.price && similarity(key(read.name), key(it.name)) >= NAME }
                }.map { it.name }
                if (reading.gapsAfterPhoto.isEmpty() && twice.isEmpty()) {
                    overlapsClean++
                } else {
                    report.append("    OVERLAP NOT CLEAN: gaps ${reading.gapsAfterPhoto}, twice $twice\n")
                }
            }

            val gap = listOfNotNull(dump("$name-gap_a"), dump("$name-gap_b")).takeIf { it.size == 2 }
            if (gap != null) {
                val reading = ReceiptParser.readParts(gap, TODAY)
                gaps++
                if (reading.gapsAfterPhoto == listOf(1)) gapsFlagged++
                report.append("  $name-gap: gaps ${reading.gapsAfterPhoto}, ${reading.items.size} items read\n")
            }
        }

        println("Local long receipts (whole photos and overlap sets)")
        bySource.forEach { (source, s) ->
            val all = maxOf(s.all, 1)
            println("  $source: ${s.right}/${s.all} items right (${s.right * 100 / all}%); price right, name half alike ${s.loose}/${s.all} (${s.loose * 100 / all}%)")
        }
        println("  overlaps joined clean: $overlapsClean/$overlaps; gaps flagged: $gapsFlagged/$gaps")
        println(report)
    }

    private companion object {
        const val NAME = 0.8
        const val LOOSE_NAME = 0.5
    }
}
