package com.dvnnfrr.saku.receipt

import com.dvnnfrr.saku.domain.Money
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Accuracy over a local test set that must never be committed: web photos of store
 * receipts and wallet screenshots, which are not ours to publish.
 *
 * Skipped unless `RECEIPT_LOCAL_SET` names one or more folders (separated by `:`), each
 * holding recognizer dumps (`<name>.txt`, as `ReceiptAccuracyTest` on the emulator writes
 * them) and a `sources.tsv` with `file` and `total_idr` columns. Prints the exact and
 * pick-or-chip rates per store or app, taken from the file name's prefix (`indomaret_02`).
 * It reports and never fails: the set is small and changes, so the bar lives in
 * [ReceiptFixtureAccuracyTest].
 */
class LocalReceiptAccuracyTest {

    @Test
    fun `report accuracy on the local set`() {
        val dirs = System.getenv("RECEIPT_LOCAL_SET")?.split(':')?.map(::File)?.filter { it.isDirectory }.orEmpty()
        assumeTrue("RECEIPT_LOCAL_SET not set; local accuracy skipped", dirs.isNotEmpty())

        data class Score(var n: Int = 0, var exact: Int = 0, var offered: Int = 0)
        val scores = sortedMapOf<String, Score>()
        val misses = mutableListOf<String>()

        for (dir in dirs) {
            val sources = File(dir, "sources.tsv").readLines()
            val header = sources.first().split('\t')
            val fileCol = header.indexOf("file")
            val totalCol = header.indexOf("total_idr")
            for (row in sources.drop(1).map { it.split('\t') }) {
                val name = row.getOrNull(fileCol)?.substringBeforeLast('.') ?: continue
                val want = row.getOrNull(totalCol)?.filter(Char::isDigit)?.toLongOrNull()?.let(::Money) ?: continue
                val dump = File(dir, "$name.txt").takeIf { it.isFile } ?: continue
                val reading = ReceiptParser.read(parseDump(dump.readText()), TODAY)
                val score = scores.getOrPut(name.substringBefore('_')) { Score() }
                score.n++
                if (reading.total == want) score.exact++ else misses += "$name: want ${want.rupiah}, got ${reading.total?.rupiah}"
                if (reading.total == want || want in reading.otherAmounts) score.offered++
            }
        }

        val n = scores.values.sumOf { it.n }
        val exact = scores.values.sumOf { it.exact }
        val offered = scores.values.sumOf { it.offered }
        println("Local set: $n images, exact $exact (${exact * 100 / maxOf(n, 1)}%), pick or chip $offered (${offered * 100 / maxOf(n, 1)}%)")
        scores.forEach { (prefix, s) -> println("  $prefix: exact ${s.exact}/${s.n}, pick or chip ${s.offered}/${s.n}") }
        misses.forEach { println("  MISS $it") }
    }
}
