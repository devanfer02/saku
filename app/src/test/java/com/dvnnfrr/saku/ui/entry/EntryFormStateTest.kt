package com.dvnnfrr.saku.ui.entry

import com.dvnnfrr.saku.domain.CategoryKind
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.Transaction
import com.dvnnfrr.saku.domain.TxnType
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers the form scenarios in specs/transaction-entry/spec.md. */
class EntryFormStateTest {

    private val now = LocalDateTime(2026, 8, 17, 9, 30)

    private fun form(
        type: TxnType = TxnType.EXPENSE,
        amount: Long? = 187_000,
        accountId: Long? = 1,
        toAccountId: Long? = null,
        categoryId: Long? = 3,
        subcategoryId: Long? = null,
        note: String = "",
    ) = EntryFormState(
        type = type,
        amount = amount?.let { AmountInput.of(Money(it)) } ?: AmountInput(),
        occurredAt = now,
        accountId = accountId,
        toAccountId = toAccountId,
        categoryId = categoryId,
        subcategoryId = subcategoryId,
        note = note,
    )

    // --- Defaults ----------------------------------------------------------

    @Test
    fun `a new sheet opens on Expense with the given time`() {
        val state = EntryFormState.recording(now, accountId = 1)

        assertEquals(TxnType.EXPENSE, state.type)
        assertEquals(now, state.occurredAt)
        assertTrue(state.amount.isEmpty)
        assertFalse(state.isEditing)
    }

    // --- Type switching ----------------------------------------------------

    @Test
    fun `switching Expense to Income keeps the amount, time and note`() {
        val state = form(note = "Padang", subcategoryId = 5).withType(TxnType.INCOME)

        assertEquals(Money(187_000), state.amount.value)
        assertEquals(now, state.occurredAt)
        assertEquals("Padang", state.note)
    }

    @Test
    fun `switching Expense to Income drops the category`() {
        val state = form(categoryId = 3, subcategoryId = 5).withType(TxnType.INCOME)

        // An expense category is not an income category; carrying it over would save
        // income against "Makan & Minum".
        assertNull(state.categoryId)
        assertNull(state.subcategoryId)
        assertEquals(CategoryKind.INCOME, state.categoryKind)
    }

    @Test
    fun `switching to Transfer drops the category and asks for a destination`() {
        val state = form(categoryId = 3, subcategoryId = 5).withType(TxnType.TRANSFER)

        assertNull(state.categoryId)
        assertNull(state.subcategoryId)
        assertNull(state.categoryKind)
        assertTrue(EntryProblem.DESTINATION_MISSING in state.problems())
    }

    @Test
    fun `switching away from Transfer drops the destination`() {
        val transfer = form(type = TxnType.TRANSFER, categoryId = null, toAccountId = 2)

        val state = transfer.withType(TxnType.EXPENSE)

        assertNull(state.toAccountId)
        // Otherwise an expense saves with a stale destination account attached.
    }

    @Test
    fun `switching to the same type changes nothing`() {
        val original = form(subcategoryId = 5)

        assertEquals(original, original.withType(TxnType.EXPENSE))
    }

    // --- Category and subcategory -----------------------------------------

    @Test
    fun `changing the category clears the subcategory`() {
        val state = form(categoryId = 3, subcategoryId = 5).withCategory(next = 4)

        assertEquals(4L, state.categoryId)
        assertNull(state.subcategoryId)
    }

    @Test
    fun `changing the category prefills the last subcategory used with it`() {
        val state = form(categoryId = 3, subcategoryId = 5).withCategory(next = 4, recalled = 9)

        assertEquals(9L, state.subcategoryId)
    }

    @Test
    fun `reselecting the same category leaves the subcategory alone`() {
        val state = form(categoryId = 3, subcategoryId = 5).withCategory(next = 3, recalled = 9)

        assertEquals(5L, state.subcategoryId)
    }

    @Test
    fun `clearing the category clears the subcategory regardless of recall`() {
        val state = form(categoryId = 3, subcategoryId = 5).withCategory(next = null, recalled = 9)

        assertNull(state.categoryId)
        assertNull(state.subcategoryId)
    }

    @Test
    fun `inline creation selects what it made and disturbs nothing else`() {
        val before = form(amount = 45_000, note = "Kopi", categoryId = null)

        val withCategory = before.withCreatedCategory(id = 12)
        assertEquals(12L, withCategory.categoryId)
        assertEquals(Money(45_000), withCategory.amount.value)
        assertEquals("Kopi", withCategory.note)

        val withSubcategory = withCategory.withCreatedSubcategory(id = 13)
        assertEquals(13L, withSubcategory.subcategoryId)
        assertEquals(12L, withSubcategory.categoryId)
        assertEquals("Kopi", withSubcategory.note)
    }

    // --- Validation --------------------------------------------------------

    @Test
    fun `a complete expense saves`() {
        assertTrue(form().canSave)
        assertEquals(emptyList<EntryProblem>(), form().problems())
    }

    @Test
    fun `an empty amount blocks the save`() {
        assertEquals(listOf(EntryProblem.AMOUNT_MISSING), form(amount = null).problems())
    }

    @Test
    fun `a zero amount blocks the save`() {
        val state = form(amount = null).copy(amount = AmountInput().digit(1).backspace().digit(0))

        // digit(0) on an empty field is ignored, so this is still the empty case —
        // which is the point: there is no way to type a bare zero.
        assertTrue(EntryProblem.AMOUNT_MISSING in state.problems())
    }

    @Test
    fun `a negative result blocks the save`() {
        val negative = AmountInput()
            .let { AmountInput(entered = "100") }
            .operator(AmountOperator.MINUS)
            .let { input -> "500".fold(input) { acc, c -> acc.digit(c - '0') } }

        val state = form().copy(amount = negative)

        assertEquals(listOf(EntryProblem.AMOUNT_NOT_POSITIVE), state.problems())
    }

    @Test
    fun `an expense with no category blocks the save`() {
        val problems = form(categoryId = null).problems()

        assertEquals(listOf(EntryProblem.CATEGORY_MISSING), problems)
        assertEquals(EntryField.CATEGORY, problems.single().field)
    }

    @Test
    fun `a transfer needs no category`() {
        val transfer = form(type = TxnType.TRANSFER, categoryId = null, toAccountId = 2)

        assertTrue(transfer.canSave)
    }

    @Test
    fun `a transfer to the same account blocks the save`() {
        val transfer = form(type = TxnType.TRANSFER, categoryId = null, accountId = 1, toAccountId = 1)

        assertEquals(listOf(EntryProblem.DESTINATION_SAME_AS_SOURCE), transfer.problems())
    }

    @Test
    fun `a missing account blocks the save`() {
        assertTrue(EntryProblem.ACCOUNT_MISSING in form(accountId = null).problems())
    }

    @Test
    fun `a subcategory is never required`() {
        assertTrue(form(subcategoryId = null).canSave)
    }

    @Test
    fun `every problem is reported at once`() {
        val problems = form(amount = null, accountId = null, categoryId = null).problems()

        // One save reveals everything wrong, rather than one field per attempt.
        assertEquals(
            listOf(
                EntryProblem.AMOUNT_MISSING,
                EntryProblem.CATEGORY_MISSING,
                EntryProblem.ACCOUNT_MISSING,
            ),
            problems,
        )
    }

    // --- Editing -----------------------------------------------------------

    @Test
    fun `editing seeds every field from the transaction`() {
        val txn = Transaction(
            id = 42,
            occurredAt = Instant.parse("2026-08-14T05:00:00Z"),
            type = TxnType.EXPENSE,
            amount = Money(170_000),
            accountId = 1,
            categoryId = 3,
            subcategoryId = 5,
            note = "Warung",
            createdAt = Instant.parse("2026-08-14T05:00:00Z"),
        )

        val state = EntryFormState.editing(txn, LocalDateTime(2026, 8, 14, 12, 0))

        assertTrue(state.isEditing)
        assertEquals(42L, state.id)
        assertEquals(Money(170_000), state.amount.value)
        assertEquals(3L, state.categoryId)
        assertEquals(5L, state.subcategoryId)
        assertEquals("Warung", state.note)
        assertTrue(state.canSave)
    }
}
