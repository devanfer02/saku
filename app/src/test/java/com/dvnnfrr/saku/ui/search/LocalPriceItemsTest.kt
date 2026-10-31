package com.dvnnfrr.saku.ui.search

import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.Transaction
import com.dvnnfrr.saku.domain.TxnType
import com.dvnnfrr.saku.receipt.itemKey
import com.dvnnfrr.saku.receipt.readableItemName
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * How price history groups a real ledger's notes. Reads a local TSV (date, type, amount,
 * category, note) named by `SAKU_LOCAL_NOTES`, never committed, and prints the items the
 * counter finds and the near misses exact matching leaves apart. A report, not a bar.
 */
class LocalPriceItemsTest {

    private val zone = TimeZone.of("Asia/Jakarta")

    @Test
    fun `report how a real ledger's notes group into items`() {
        val file = System.getenv("SAKU_LOCAL_NOTES")?.let(::File)?.takeIf { it.isFile }
        assumeTrue("SAKU_LOCAL_NOTES not set; local price items skipped", file != null)

        val rows = file!!.readLines().drop(1).mapIndexedNotNull { i, line ->
            val cells = line.split('\t')
            if (cells.size < 5) return@mapIndexedNotNull null
            val type = if (cells[1] == "Expense") TxnType.EXPENSE else TxnType.INCOME
            val at = LocalDateTime.parse(cells[0].replace(' ', 'T')).toInstant(zone)
            Transaction(
                id = i.toLong() + 1, occurredAt = at, type = type, amount = Money(cells[2].toLong()),
                accountId = 1, categoryId = 1, note = cells[4], createdAt = at,
            )
        }
        val expenses = rows.filter { it.type == TxnType.EXPENSE }
        val items = rows.priceItems()
        val inItems = items.sumOf { it.count }
        val keys = expenses.map { priceKey(it.note) }.filter { it.isNotEmpty() }.groupingBy { it }.eachCount()
        // Word-level names for the near-miss check below; price keys have no spaces.
        val names = expenses.map { itemKey(readableItemName(it.note.orEmpty())) }
            .filter { it.isNotEmpty() }.groupingBy { it }.eachCount()

        val report = StringBuilder()
        report.append("Expenses ${expenses.size}, distinct notes ${keys.size}, ")
        report.append("items bought twice or more ${items.size} covering $inItems rows\n")
        report.append("Top items:\n")
        items.take(40).forEach { item ->
            val card = rows.priceHistory(zone, key = item.key)!!
            report.append("  ${item.count.toString().padStart(3)}  ${item.name}  ")
            report.append("low ${card.lowest.unitPrice.rupiah} high ${card.highest.unitPrice.rupiah} latest ${card.latest.unitPrice.rupiah}\n")
        }
        // Near misses: two keys where one is the other plus words, which exact matching keeps apart.
        report.append("Near misses (one name contains the other):\n")
        val sorted = names.keys.sortedBy { it.length }
        var shown = 0
        for (short in sorted) {
            for (long in sorted) {
                if (shown >= 40) break
                if (long.length > short.length && long.startsWith("$short ")) {
                    report.append("  $short (${names[short]})  vs  $long (${names[long]})\n")
                    shown++
                }
            }
        }
        println(report)
    }
}
