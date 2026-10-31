package com.dvnnfrr.saku.ui.search

import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.Transaction
import com.dvnnfrr.saku.domain.TransactionFilter
import com.dvnnfrr.saku.domain.TxnType
import com.dvnnfrr.saku.receipt.itemQuantity
import com.dvnnfrr.saku.ui.transactions.DisplayIndex
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import com.dvnnfrr.saku.receipt.LearnedWords
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Every scenario of `specs/item-price-history/spec.md` that the assembly decides. */
class PriceHistoryAssemblyTest {

    private val zone = TimeZone.of("Asia/Jakarta")
    private var nextId = 1L

    private fun txn(
        note: String?,
        amount: Long,
        date: LocalDate,
        type: TxnType = TxnType.EXPENSE,
        hour: Int = 12,
    ): Transaction {
        val instant = LocalDateTime(date.year, date.month, date.dayOfMonth, hour, 0).toInstant(zone)
        return Transaction(
            id = nextId++,
            occurredAt = instant,
            type = type,
            amount = Money(amount),
            accountId = 1,
            toAccountId = if (type == TxnType.TRANSFER) 2 else null,
            categoryId = if (type.requiresCategory) 1 else null,
            note = note,
            createdAt = instant,
        )
    }

    private fun day(month: Int, day: Int = 10) = LocalDate(2026, month, day)

    @Test
    fun `a quantity suffix divides the amount, rounded half up`() {
        assertEquals(Money(14_767), unitPrice(Money(29_534), "SUSU UHT 1L ×2"))
        assertEquals(Money(18_200), unitPrice(Money(18_200), "susu uht 1l"))
        assertEquals(Money(3_334), unitPrice(Money(10_001), "TELUR x 3"))
        assertEquals(Money(5), unitPrice(Money(9), "PERMEN ×2"))
        assertEquals(Money(9), unitPrice(Money(9), null))
    }

    @Test
    fun `quantity reads only a trailing suffix from 1 to 999`() {
        assertEquals(2, itemQuantity("SUSU UHT 1L ×2"))
        assertEquals(12, itemQuantity("susu uht 1l X12 "))
        // The owner's notes put the count first: "Indomie 2X", "Dimsum 3 biji", "Dimsum 20PCS".
        assertEquals(2, itemQuantity("MIE 2X"))
        assertEquals(3, itemQuantity("Dimsum 3 biji"))
        assertEquals(20, itemQuantity("Dimsum 20PCS"))
        assertEquals(2, itemQuantity("Nasi Goreng 2 porsi"))
        assertEquals(1, itemQuantity("TELUR AYAM 10"))
        assertEquals(1, itemQuantity("AIR ×0"))
        assertEquals(1, itemQuantity("AIR ×1000"))
    }

    @Test
    fun `spec scenario, a x2 row and a plain row are one item`() {
        val card = listOf(
            txn("SUSU UHT 1L ×2", 29_534, day(9)),
            txn("susu uht 1l", 18_200, day(10)),
        ).priceHistory(zone)
        assertNotNull(card)
        card!!
        assertEquals("SUSUUHT1L", card.itemKey)
        assertEquals(listOf(Money(14_767), Money(18_200)), card.points.map { it.unitPrice })
    }

    @Test
    fun `spec scenario, four rows from July to October`() {
        val card = listOf(
            txn("MINYAK GORENG 2L", 41_000, day(10)),
            txn("MINYAK GORENG 2L", 35_000, day(7)),
            txn("Minyak Goreng 2L", 38_900, day(9)),
            txn("MINYAK GORENG 2L", 36_000, day(8)),
        ).priceHistory(zone)!!

        assertEquals(4, card.points.size)
        assertEquals(day(7), card.first.date)
        assertEquals(PricePoint(day(10), Money(41_000)), card.latest)
        assertEquals(PricePoint(day(7), Money(35_000)), card.lowest)
        assertEquals(PricePoint(day(10), Money(41_000)), card.highest)
        assertEquals(Money(6_000), card.change)
        assertEquals(17, card.changePercent)
    }

    @Test
    fun `two rows on one day are one point at their average`() {
        val card = listOf(
            txn("KOPI", 10_000, day(10), hour = 9),
            txn("KOPI ×2", 25_001, day(10), hour = 18),
            txn("KOPI", 12_000, day(11)),
        ).priceHistory(zone)!!
        // 10.000 and 12.500,5 -> 12.501; mean 11.250,5 rounds up.
        assertEquals(listOf(PricePoint(day(10), Money(11_251)), PricePoint(day(11), Money(12_000))), card.points)
    }

    @Test
    fun `two items bought twice each give chips, most rows first`() {
        val rows = listOf(
            txn("MINYAK GORENG 2L", 38_900, day(7)),
            txn("MINYAK GORENG 1L", 20_000, day(8)),
            txn("Minyak Goreng 2L ×2", 77_800, day(9)),
            txn("MINYAK GORENG 1L", 21_000, day(10)),
            txn("minyak goreng 2l", 39_000, day(11)),
        )
        val items = rows.priceItems()
        assertEquals(listOf("MINYAKGORENG2L" to 3, "MINYAKGORENG1L" to 2), items.map { it.key to it.count })
        assertEquals("Minyak Goreng 2L", items.first().name)

        assertEquals("MINYAKGORENG2L", rows.priceHistory(zone)?.itemKey)
        assertEquals("MINYAKGORENG1L", rows.priceHistory(zone, key = "MINYAKGORENG1L")?.itemKey)
        // A picked key no longer among the items falls back to the most bought one.
        assertEquals("MINYAKGORENG2L", rows.priceHistory(zone, key = "GULA1KG")?.itemKey)
    }

    @Test
    fun `items bought once give no card`() {
        val card = listOf(
            txn("MINYAK GORENG 2L", 38_900, day(9)),
            txn("MINYAK GORENG 1L", 20_000, day(10)),
        ).priceHistory(zone)
        assertNull(card)
    }

    @Test
    fun `short forms and learned words join one item, other words do not`() {
        val rows = listOf(
            txn("INDOMIE GRG SPCL ×2", 6_200, day(8)),
            txn("Indomie goreng special", 3_200, day(9)),
            txn("Indomie Goreng", 3_000, day(10)),
        )
        assertEquals(listOf("INDOMIEGORENGSPECIAL" to 2), rows.priceItems().map { it.key to it.count })

        val learned = LearnedWords(words = mapOf("BRS" to "Buris"))
        val rice = listOf(txn("BRS PREMIUM 5KG", 72_500, day(8)), txn("Buris Premium 5KG", 73_000, day(9)))
        assertEquals(2, rice.priceItems(learned).single().count)
        assertTrue(rice.priceItems().isEmpty())
    }

    @Test
    fun `spaces and a count written first do not split an item`() {
        val rows = listOf(
            txn("Es Teh", 5_000, day(8)),
            txn("Esteh 2X", 10_000, day(9)),
            txn("es teh", 6_000, day(10)),
        )
        val item = rows.priceItems().single()
        assertEquals(3, item.count)
        assertEquals(Money(5_000), rows.priceHistory(zone)!!.points[1].unitPrice)
    }

    @Test
    fun `one row shows no card`() {
        assertNull(listOf(txn("MINYAK GORENG 2L", 38_900, day(9))).priceHistory(zone))
    }

    @Test
    fun `income and transfers are ignored, blank notes are not an item`() {
        val withIncome = listOf(
            txn("GULA 1KG", 18_000, day(8)),
            txn("GULA 1KG", 19_000, day(9)),
            txn("GULA 1KG refund", 18_000, day(9), type = TxnType.INCOME),
        ).priceHistory(zone)
        assertEquals(2, withIncome?.points?.size)

        assertNull(listOf(txn(null, 5_000, day(8)), txn("  ", 5_000, day(9))).priceHistory(zone))
    }

    @Test
    fun `lowest and highest name the latest day at that price`() {
        val card = listOf(
            txn("ROTI", 15_000, day(7)),
            txn("ROTI", 17_000, day(8)),
            txn("ROTI", 15_000, day(9)),
        ).priceHistory(zone)!!
        assertEquals(day(9), card.lowest.date)
        assertEquals(day(8), card.highest.date)
    }

    @Test
    fun `change percent rounds half away from zero and falls with the price`() {
        assertEquals(11, percentOf(Money(3_900), Money(35_000)))
        assertEquals(-10, percentOf(Money(-3_900), Money(38_900)))
        assertEquals(1, percentOf(Money(5), Money(1_000)))
        assertEquals(-1, percentOf(Money(-5), Money(1_000)))
        assertEquals(0, percentOf(Money.ZERO, Money(1_000)))
        assertNull(percentOf(Money(100), Money.ZERO))
    }

    @Test
    fun `search carries the card of the most bought item`() {
        val rows = listOf(
            txn("MINYAK GORENG 2L", 35_000, day(7)),
            txn("MINYAK GORENG 2L ×2", 77_800, day(9)),
            txn("MINYAK GORENG 1L", 20_000, day(9)),
        ).searchable()
        val index = DisplayIndex(emptyList(), emptyList())
        val one = assembleSearch("minyak goreng 2l", TransactionFilter.ALL, rows, null, zone, index)
        assertEquals(Money(38_900), one.price?.latest?.unitPrice)

        val two = assembleSearch("minyak", TransactionFilter.ALL, rows, null, zone, index)
        assertEquals("MINYAKGORENG2L", two.price?.itemKey)
        assertEquals(1, two.priceItems.size)
        assertEquals(3, two.count)
    }
}
