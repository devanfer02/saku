package com.dvnnfrr.saku.ui.entry

import com.dvnnfrr.saku.domain.Category
import com.dvnnfrr.saku.domain.CategoryKind
import com.dvnnfrr.saku.domain.ColorSlot
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.NoteUsage
import com.dvnnfrr.saku.domain.TxnType
import com.dvnnfrr.saku.domain.fold
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers the ranking and fill scenarios in specs/note-autocomplete/spec.md. */
class NoteSuggestionAssemblyTest {

    private val kendaraan = category(1, "Kendaraan Pribadi")
    private val kos = category(2, "Kebutuhan Kos")
    private val lama = category(3, "Lama", archived = true)
    private val active = listOf(kendaraan, kos, lama)

    private fun category(id: Long, name: String, archived: Boolean = false) =
        Category(id, name, CategoryKind.EXPENSE, ColorSlot(id.toInt()), id.toInt(), archived)

    private fun usage(
        note: String,
        count: Int = 1,
        lastAt: Long = 0,
        amount: Long = 10_000,
        categoryId: Long? = kendaraan.id,
    ) = NoteUsage(fold(note), note, count, Instant.fromEpochSeconds(lastAt), Money(amount), categoryId)

    private fun notes(usages: List<NoteUsage>, typed: String) =
        suggestNotes(usages, typed, active).map { it.note }

    // --- Ranking -----------------------------------------------------------

    @Test
    fun `more uses rank first`() {
        val usages = listOf(usage("beli galon", count = 3), usage("bensin motor", count = 22))

        assertEquals(listOf("bensin motor", "beli galon"), notes(usages, "be"))
    }

    @Test
    fun `prefix matches rank above contains matches whatever their counts`() {
        val usages = listOf(usage("es teh", count = 40), usage("teh botol", count = 2))

        assertEquals(listOf("teh botol", "es teh"), notes(usages, "teh"))
    }

    @Test
    fun `a count tie goes to the more recent note`() {
        val usages = listOf(usage("susu uht", count = 2, lastAt = 100), usage("susu kotak", count = 2, lastAt = 200))

        assertEquals(listOf("susu kotak", "susu uht"), notes(usages, "susu"))
    }

    @Test
    fun `a full tie is alphabetical so the order is stable`() {
        val usages = listOf(usage("parkir motor"), usage("parkir mall"))

        assertEquals(listOf("parkir mall", "parkir motor"), notes(usages, "pa"))
    }

    @Test
    fun `case, accents and spaces in the typed text are ignored`() {
        val usages = listOf(usage("Uang Arisan"), usage("Café Batavia"))

        assertEquals(listOf("Uang Arisan"), notes(usages, "  UANG "))
        assertEquals(listOf("Café Batavia"), notes(usages, "cafe"))
    }

    @Test
    fun `fewer than two characters suggests nothing`() {
        val usages = listOf(usage("bensin motor"))

        assertTrue(notes(usages, "b").isEmpty())
        assertTrue(notes(usages, " b ").isEmpty())
        assertTrue(notes(usages, "").isEmpty())
    }

    @Test
    fun `at most five, the best ranked`() {
        val usages = (1..8).map { usage("makan $it", count = it) }

        assertEquals(listOf("makan 8", "makan 7", "makan 6", "makan 5", "makan 4"), notes(usages, "makan"))
    }

    @Test
    fun `an exact match is still offered`() {
        assertEquals(listOf("laundry"), notes(listOf(usage("laundry")), "Laundry"))
    }

    @Test
    fun `non-matching notes are left out`() {
        assertTrue(notes(listOf(usage("laundry")), "xy").isEmpty())
    }

    // --- What a suggestion shows ---------------------------------------------

    @Test
    fun `a suggestion carries its active category and last amount`() {
        val suggestion = suggestNotes(listOf(usage("laundry", amount = 35_000, categoryId = kos.id)), "la", active).single()

        assertEquals(kos, suggestion.category)
        assertEquals(Money(35_000), suggestion.lastAmount)
    }

    @Test
    fun `an archived category is not shown`() {
        val suggestion = suggestNotes(listOf(usage("tiket lama", categoryId = lama.id)), "ti", active).single()

        assertNull(suggestion.category)
    }

    @Test
    fun `a category missing from the active list is not shown`() {
        val suggestion = suggestNotes(listOf(usage("tiket", categoryId = 99)), "ti", active).single()

        assertNull(suggestion.category)
    }

    // --- Fill ----------------------------------------------------------------

    private val now = LocalDateTime(2026, 10, 30, 9, 0)

    private fun form(amount: AmountInput = AmountInput(), categoryId: Long? = null) = EntryFormState(
        type = TxnType.EXPENSE,
        amount = amount,
        occurredAt = now,
        accountId = 7,
        categoryId = categoryId,
        note = "bens",
    )

    private val bensin = NoteSuggestion("bensin motor", "bensin motor", kendaraan, Money(30_000))

    @Test
    fun `picking on an empty amount fills note, category and amount and keeps the account`() {
        val filled = applySuggestion(form(), bensin)

        assertEquals("bensin motor", filled.note)
        assertEquals(kendaraan.id, filled.categoryId)
        assertEquals(Money(30_000), filled.amount.value)
        assertEquals(7L, filled.accountId)
        assertEquals(now, filled.occurredAt)
        assertEquals(TxnType.EXPENSE, filled.type)
    }

    @Test
    fun `an amount already typed is kept`() {
        val filled = applySuggestion(form(amount = AmountInput.of(Money(50_000))), bensin)

        assertEquals(Money(50_000), filled.amount.value)
        assertEquals(kendaraan.id, filled.categoryId)
        assertEquals("bensin motor", filled.note)
    }

    @Test
    fun `an amount mid-expression is kept`() {
        val pending = AmountInput().digit(5).operator(AmountOperator.PLUS)

        assertEquals(pending, applySuggestion(form(amount = pending), bensin).amount)
    }

    @Test
    fun `a typed zero counts as empty`() {
        val zero = AmountInput().digit(0)

        assertEquals(Money(30_000), applySuggestion(form(amount = zero), bensin).amount.value)
    }

    @Test
    fun `the category replaces one already chosen`() {
        assertEquals(kendaraan.id, applySuggestion(form(categoryId = kos.id), bensin).categoryId)
    }

    @Test
    fun `no active category fills the note only`() {
        val start = form(categoryId = kos.id)

        val filled = applySuggestion(start, bensin.copy(category = null))

        assertEquals(start.copy(note = "bensin motor"), filled)
    }
}
