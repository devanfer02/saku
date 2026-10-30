package com.dvnnfrr.saku.ui.search

import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.Transaction
import com.dvnnfrr.saku.domain.TransactionFilter
import com.dvnnfrr.saku.domain.TxnType
import com.dvnnfrr.saku.ui.transactions.DisplayIndex
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every scenario of `specs/transaction-search/spec.md` that matching decides. */
class SearchAssemblyTest {

    private val zone = TimeZone.of("Asia/Jakarta")
    private val index = DisplayIndex(emptyList(), emptyList())
    private var nextId = 1L

    private fun txn(
        note: String?,
        amount: Long = 10_000,
        type: TxnType = TxnType.EXPENSE,
        at: LocalDateTime = LocalDateTime(2026, 10, 1, 12, 0),
    ): Transaction {
        val instant = at.toInstant(zone)
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

    private fun search(query: String, vararg txns: Transaction) =
        assembleSearch(query, TransactionFilter.ALL, txns.toList().searchable(), null, zone, index)

    private fun ids(query: String, vararg txns: Transaction) =
        search(query, *txns).days.flatMap { it.rows }.map { it.id }

    @Test
    fun `case is ignored`() {
        val kopi = txn("Kopi Kenangan")
        assertEquals(listOf(kopi.id), ids("kopi", kopi))
    }

    @Test
    fun `accents are ignored both ways`() {
        val accented = txn("Café Batavia")
        val plain = txn("cafe")
        assertEquals(listOf(accented.id), ids("cafe", accented))
        assertEquals(listOf(plain.id), ids("café", plain))
    }

    @Test
    fun `every word must match`() {
        val first = txn("kopi susu gula aren")
        val second = txn("kopi hitam")
        assertEquals(listOf(first.id), ids("kopi susu", first, second))
    }

    @Test
    fun `part of a word matches`() {
        val kopi = txn("Kopi Kenangan")
        assertEquals(listOf(kopi.id), ids("kenang", kopi))
    }

    @Test
    fun `a blank query shows no results`() {
        val state = search("   ", txn("kopi"))
        assertFalse(state.searched)
        assertTrue(state.days.isEmpty())
    }

    @Test
    fun `an exact amount with separators matches`() {
        val meal = txn(null, amount = 47_500)
        assertEquals(listOf(meal.id), ids("47.500", meal))
    }

    @Test
    fun `a partial amount matches by digits`() {
        val a = txn(null, amount = 47_500)
        val b = txn(null, amount = 1_475_000)
        val c = txn(null, amount = 45_700)
        assertEquals(setOf(a.id, b.id), ids("475", a, b, c).toSet())
    }

    @Test
    fun `amount and text together`() {
        val cheap = txn("Grab ke kampus", amount = 25_000)
        val dear = txn("Grab pulang", amount = 32_000)
        assertEquals(listOf(cheap.id), ids("grab 25000", cheap, dear))
    }

    @Test
    fun `the rupiah prefix is ignored`() {
        val ride = txn("Grab", amount = 25_000)
        val other = txn("Gojek", amount = 32_000)
        assertEquals(ids("25000", ride, other), ids("Rp25.000", ride, other))
        assertEquals(ids("25000", ride, other), ids("Rp 25.000", ride, other))
        assertEquals(listOf(ride.id), ids("Rp25.000", ride, other))
    }

    @Test
    fun `a digits word also matches the note`() {
        val noted = txn("token 2024", amount = 50_000)
        assertEquals(listOf(noted.id), ids("2024", noted))
    }

    @Test
    fun `matches across years come newest first, counted`() {
        val march = txn("kopi", at = LocalDateTime(2025, 3, 4, 9, 0))
        val october = txn("kopi", at = LocalDateTime(2026, 10, 4, 9, 0))

        val state = search("kopi", march, october)

        assertEquals(listOf(october.id, march.id), state.days.flatMap { it.rows }.map { it.id })
        assertEquals(2, state.count)
    }

    @Test
    fun `transfers are listed but count toward neither total`() {
        val spend = txn("tabungan", amount = 100_000)
        val transfer = txn("tabungan", amount = 500_000, type = TxnType.TRANSFER)

        val state = search("tabungan", spend, transfer)

        assertEquals(2, state.count)
        assertEquals(Money(100_000), state.totals.expense)
        assertEquals(Money.ZERO, state.totals.income)
    }

    @Test
    fun `matches the filter hides are counted`() {
        val cash = (1..3).map { txn("kopi") }
        val bca = (1..2).map { txn("kopi") }

        val state = assembleSearch(
            query = "kopi",
            filter = TransactionFilter(accountIds = setOf(1)),
            filtered = cash.searchable(),
            unfiltered = (cash + bca).searchable(),
            zone = zone,
            index = index,
        )

        assertEquals(3, state.count)
        assertEquals(2, state.hiddenCount)
    }

    @Test
    fun `nothing matching at all is an empty search, not a blank one`() {
        val state = search("zzz", txn("kopi"))
        assertTrue(state.searched)
        assertEquals(0, state.count)
        assertEquals(0, state.hiddenCount)
    }
}
