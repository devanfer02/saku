package com.dvnnfrr.saku.ui.entry

import com.dvnnfrr.saku.domain.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers the keypad scenarios in specs/transaction-entry/spec.md. */
class AmountInputTest {

    private fun type(digits: String, from: AmountInput = AmountInput()): AmountInput =
        digits.fold(from) { acc, c -> acc.digit(c - '0') }

    @Test
    fun `an untouched field is empty rather than zero`() {
        val input = AmountInput()

        assertTrue(input.isEmpty)
        assertEquals(Money.ZERO, input.value)
    }

    @Test
    fun `the 000 key appends three zeros`() {
        val input = type("187").tripleZero()

        assertEquals(Money(187_000), input.value)
    }

    @Test
    fun `the 000 key is inert on an empty field`() {
        val input = AmountInput().tripleZero()

        // Otherwise the field reads 000, which is a typed zero and gets rejected on
        // save — a confusing outcome for a key the user pressed by mistake.
        assertTrue(input.isEmpty)
    }

    @Test
    fun `a leading zero is ignored`() {
        assertTrue(AmountInput().digit(0).isEmpty)
        assertEquals(Money(7), type("07").value)
    }

    @Test
    fun `inline addition evaluates on save`() {
        val input = type("16000")
            .operator(AmountOperator.PLUS)
            .let { type("14000", it) }

        assertEquals(Money(30_000), input.value)
    }

    @Test
    fun `inline subtraction evaluates on save`() {
        val input = type("50000")
            .operator(AmountOperator.MINUS)
            .let { type("12500", it) }

        assertEquals(Money(37_500), input.value)
    }

    @Test
    fun `chained arithmetic runs left to right`() {
        val input = type("100")
            .operator(AmountOperator.PLUS)
            .let { type("50", it) }
            .operator(AmountOperator.MINUS)
            .let { type("20", it) }

        assertEquals(Money(130), input.value)
    }

    @Test
    fun `an operator with nothing typed yet is ignored`() {
        val input = AmountInput().operator(AmountOperator.PLUS)

        assertTrue(input.isEmpty)
        assertNull(input.pendingOperator)
    }

    @Test
    fun `pressing a second operator replaces the first`() {
        val input = type("100")
            .operator(AmountOperator.PLUS)
            .operator(AmountOperator.MINUS)
            .let { type("40", it) }

        assertEquals(Money(60), input.value)
    }

    @Test
    fun `backspace deletes one digit`() {
        assertEquals(Money(18), type("187").backspace().value)
    }

    @Test
    fun `backspace on an empty operand takes back the operator`() {
        val input = type("187").operator(AmountOperator.PLUS).backspace()

        assertNull(input.pendingOperator)
        assertEquals(Money(187), input.value)
        // And the restored total is editable again, not frozen as an accumulator.
        assertEquals(Money(1_875), input.digit(5).value)
    }

    @Test
    fun `backspace on a wholly empty field does nothing`() {
        assertEquals(AmountInput(), AmountInput().backspace())
    }

    @Test
    fun `an expression mid-entry reports itself as pending`() {
        val input = type("100").operator(AmountOperator.PLUS)

        assertTrue(input.hasPendingExpression)
        assertFalse(input.isEmpty)
        // The committed half stands alone until the next operand arrives.
        assertEquals(Money(100), input.value)
    }

    @Test
    fun `digits stop at the cap rather than overflowing`() {
        val input = type("9".repeat(20))

        assertEquals(15, input.entered.length)
        assertTrue(input.value.rupiah > 0)
    }

    @Test
    fun `the 000 key respects the cap`() {
        val input = type("9".repeat(14)).tripleZero()

        // 14 + 3 would exceed 15, so the key is refused rather than truncating.
        assertEquals(14, input.entered.length)
    }

    @Test
    fun `an existing amount seeds the field`() {
        assertEquals(Money(187_000), AmountInput.of(Money(187_000)).value)
        assertTrue(AmountInput.of(Money.ZERO).isEmpty)
    }

    @Test
    fun `subtracting past zero yields a negative that validation can reject`() {
        val input = type("100").operator(AmountOperator.MINUS).let { type("500", it) }

        // The keypad does not clamp. Rejecting it is EntryFormState's job, so the
        // user sees why rather than watching the figure silently change.
        assertEquals(Money(-400), input.value)
    }
}
