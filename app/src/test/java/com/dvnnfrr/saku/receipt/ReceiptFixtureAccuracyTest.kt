package com.dvnnfrr.saku.receipt

import com.dvnnfrr.saku.domain.Money
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The accuracy bar, over the 100 receipts of the CORD v2 test split.
 *
 * The fixtures are what ML Kit 16.0.1 read from those images on the emulator, so this
 * measures the parser alone, with recognition held fixed. About 80% of these receipts
 * have the true total readable anywhere at all; that is the ceiling. See CORD-NOTICE.md
 * for the licence and design.md (receipt-scan) for how the bar was set.
 *
 * Raise the bars as the parser improves. Lowering one needs a reason in design.md.
 */
class ReceiptFixtureAccuracyTest {

    private data class Case(val name: String, val total: Money, val lines: List<OcrLine>)

    private val cases: List<Case> by lazy {
        val totals = resource("totals.tsv").lines().drop(1)
            .mapNotNull { line -> line.split('\t').takeIf { it.size == 2 } }
            .mapNotNull { (name, total) -> total.toLongOrNull()?.let { name to Money(it) } }
        totals.map { (name, total) -> Case(name, total, parseDump(resource("$name.txt"))) }
    }

    private fun resource(name: String): String =
        requireNotNull(javaClass.getResource("/receipt/cord/$name")) { "Missing fixture receipt/cord/$name" }.readText()

    @Test
    fun `the parser meets the accuracy bar on the CORD fixtures`() {
        var exact = 0
        var offered = 0
        val misses = mutableListOf<String>()
        for (case in cases) {
            val reading = ReceiptParser.read(case.lines, TODAY)
            if (reading.total == case.total) exact++ else misses += "${case.name}: want ${case.total.rupiah}, got ${reading.total?.rupiah}"
            if (reading.total == case.total || case.total in reading.otherAmounts) offered++
        }
        val n = cases.size
        val exactRate = exact * 100 / n
        val offeredRate = offered * 100 / n
        val report = "CORD fixtures: $n receipts, exact total $exact ($exactRate%), " +
            "pick or chip $offered ($offeredRate%)\n" + misses.joinToString("\n")
        println(report)

        assertTrue("expected 95 labelled receipts, found $n", n >= 95)
        assertTrue("exact total $exactRate% is under the 70% bar\n$report", exactRate >= EXACT_BAR)
        assertTrue("pick or chip $offeredRate% is under the 75% bar\n$report", offeredRate >= OFFERED_BAR)
    }

    private companion object {
        const val EXACT_BAR = 70
        const val OFFERED_BAR = 75
    }
}
