package com.dvnnfrr.saku.ui.entry

import com.dvnnfrr.saku.domain.Money

enum class AmountOperator { PLUS, MINUS }

/**
 * The keypad's amount field, including its inline arithmetic.
 *
 * Pure and immutable — every key is a function from one state to the next, so the
 * whole keypad is testable without a composable. Nothing here formats: grouping
 * digits with full stops is the theme's `formatIdr`, and duplicating it would give
 * the app two currency formatters that could disagree.
 *
 * Arithmetic evaluates strictly left to right. There is no precedence to get wrong
 * because the spec asks for `+` and `−` and nothing else.
 */
data class AmountInput(
    /** Digits of the operand being typed. Empty means nothing typed yet. */
    val entered: String = "",
    /** Everything already committed by an operator key. */
    val accumulated: Long = 0L,
    val pendingOperator: AmountOperator? = null,
) {
    /**
     * Long is 19 digits; 15 keeps the largest expressible amount at 999 trillion
     * rupiah and leaves room for the running total not to overflow mid-expression.
     */
    private val maxDigits = 15

    val currentOperand: Long get() = entered.toLongOrNull() ?: 0L

    /** Left to right, so `100 + 50 − 20` is 130 whichever order the keys came in. */
    val value: Money
        get() = Money(
            when (pendingOperator) {
                null -> currentOperand
                AmountOperator.PLUS -> accumulated + currentOperand
                AmountOperator.MINUS -> accumulated - currentOperand
            },
        )

    /** Nothing typed at all — distinct from a typed zero, which is a real rejection. */
    val isEmpty: Boolean get() = entered.isEmpty() && pendingOperator == null

    val hasPendingExpression: Boolean get() = pendingOperator != null

    fun digit(digit: Int): AmountInput {
        require(digit in 0..9) { "Not a digit: $digit" }
        // A leading zero would let "0", "00", "007" all mean zero and make backspace
        // behave differently depending on invisible history.
        if (entered.isEmpty() && digit == 0) return this
        if (entered.length >= maxDigits) return this
        return copy(entered = entered + digit)
    }

    /** The `000` key. Inert on an empty field, where it would only enter zeros. */
    fun tripleZero(): AmountInput {
        if (entered.isEmpty()) return this
        if (entered.length + 3 > maxDigits) return this
        return copy(entered = entered + "000")
    }

    /**
     * Deletes the last digit; on an empty operand it takes back the operator instead,
     * restoring the accumulated total as the typed operand. Without that, an
     * accidental `+` would be unrecoverable without clearing the whole field.
     */
    fun backspace(): AmountInput = when {
        entered.isNotEmpty() -> copy(entered = entered.dropLast(1))
        pendingOperator != null -> AmountInput(
            entered = if (accumulated == 0L) "" else accumulated.toString(),
            accumulated = 0L,
            pendingOperator = null,
        )
        else -> this
    }

    /**
     * Commits the typed operand and arms the next one. Pressing an operator twice
     * replaces it rather than committing zero, which is what every calculator does
     * and what a mis-tap needs.
     */
    fun operator(operator: AmountOperator): AmountInput {
        if (entered.isEmpty()) {
            return if (pendingOperator == null) this else copy(pendingOperator = operator)
        }
        return AmountInput(
            entered = "",
            accumulated = value.rupiah,
            pendingOperator = operator,
        )
    }

    fun clear(): AmountInput = AmountInput()

    companion object {
        /** Seeds the field when the sheet opens on an existing transaction. */
        fun of(money: Money): AmountInput =
            if (money.rupiah == 0L) AmountInput() else AmountInput(entered = money.rupiah.toString())
    }
}
