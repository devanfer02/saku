package com.dvnnfrr.saku.ui.entry

import androidx.annotation.StringRes
import com.dvnnfrr.saku.R
import com.dvnnfrr.saku.domain.TxnType

/**
 * Where the validation enums meet copy.
 *
 * [EntryFormState] carries no `R` reference so it stays a pure JVM type; this file is
 * the one place that maps its cases to strings, which keeps the mapping exhaustive —
 * adding a case without copy fails to compile.
 */
@StringRes
fun EntryProblem.messageRes(): Int = when (this) {
    EntryProblem.AMOUNT_MISSING -> R.string.entry_error_amount_missing
    EntryProblem.AMOUNT_NOT_POSITIVE -> R.string.entry_error_amount_not_positive
    EntryProblem.CATEGORY_MISSING -> R.string.entry_error_category_missing
    EntryProblem.ACCOUNT_MISSING -> R.string.entry_error_account_missing
    EntryProblem.DESTINATION_MISSING -> R.string.entry_error_destination_missing
    EntryProblem.DESTINATION_SAME_AS_SOURCE -> R.string.entry_error_destination_same
}

/** The three types the sheet offers, in the order the segmented control shows them. */
val ENTRY_TYPES: List<TxnType> = listOf(TxnType.EXPENSE, TxnType.INCOME, TxnType.TRANSFER)

@StringRes
fun TxnType.entryLabelRes(): Int = when (this) {
    TxnType.EXPENSE -> R.string.entry_type_expense
    TxnType.INCOME -> R.string.entry_type_income
    TxnType.TRANSFER -> R.string.entry_type_transfer
    // Adjustments are created by reconciliation, never chosen here, but an edit can
    // open one — it keeps its type and the selector simply shows nothing selected.
    TxnType.ADJUSTMENT -> R.string.entry_type_expense
}
