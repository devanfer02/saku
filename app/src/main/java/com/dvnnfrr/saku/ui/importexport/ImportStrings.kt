package com.dvnnfrr.saku.ui.importexport

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.dvnnfrr.saku.R
import com.dvnnfrr.saku.domain.TxnType
import com.dvnnfrr.saku.excel.RejectedRow
import com.dvnnfrr.saku.excel.RowRejection
import com.dvnnfrr.saku.excel.SheetLayout
import com.dvnnfrr.saku.ui.theme.formatIdr
import kotlinx.datetime.LocalDate

/**
 * Where the import model meets copy.
 *
 * The `excel` package holds no user-facing English at all — it deals in enums and
 * structured values, which is what lets it be tested against the real export without a
 * resource loader, and what stops a parser change quietly rewriting a sentence. Every
 * mapping lives here and is exhaustive, so adding a case without copy fails to compile.
 */

@StringRes
fun ImportEntryPoint.titleRes(): Int = when (this) {
    ImportEntryPoint.MONEY_MANAGER -> R.string.more_import_money_manager
    ImportEntryPoint.EXCEL -> R.string.more_import_excel
}

/** The row label for a type's count on the finished screen. */
@StringRes
fun TxnType.addedRes(): Int = when (this) {
    TxnType.EXPENSE -> R.string.import_done_expenses
    TxnType.INCOME -> R.string.import_done_income
    TxnType.TRANSFER -> R.string.import_done_transfers
    TxnType.ADJUSTMENT -> R.string.import_done_adjustments
}

@StringRes
fun ImportEntryPoint.introRes(): Int = when (this) {
    ImportEntryPoint.MONEY_MANAGER -> R.string.import_intro_money_manager
    ImportEntryPoint.EXCEL -> R.string.import_intro_excel
}

/**
 * The refusal names both the columns Saku expected and the ones it found, so the user
 * can see which column was renamed rather than being told the file is simply wrong.
 */
@Composable
fun ReadFailure.message(fileName: String): String = when (this) {
    is ReadFailure.UnrecognisedLayout -> stringResource(R.string.import_refused_layout, fileName)
    is ReadFailure.Unreadable -> stringResource(R.string.import_refused_unreadable, fileName)
    ReadFailure.NoUsableRows -> stringResource(R.string.import_refused_no_rows, fileName)
}

@Composable
fun ReadFailure.UnrecognisedLayout.expectedAndFound(): Pair<String, String> {
    val expected = stringResource(
        R.string.import_refused_expected,
        mismatch.expected[SheetLayout.MONEY_MANAGER].orEmpty().joinToString(" · "),
        mismatch.expected[SheetLayout.SAKU].orEmpty().joinToString(" · "),
    )
    val found = stringResource(
        R.string.import_refused_found,
        mismatch.found.joinToString(" · ").ifEmpty { "(no column names)" },
    )
    return expected to found
}

@Composable
fun RejectedRow.line(): String =
    stringResource(R.string.import_held_back_row, sourceRow, reasonText())

@Composable
private fun RejectedRow.reasonText(): String = when (reason) {
    RowRejection.NON_IDR_CURRENCY ->
        stringResource(R.string.import_reason_non_idr, detail.orEmpty())

    RowRejection.MISSING_DATE -> stringResource(R.string.import_reason_missing_date)
    RowRejection.MISSING_AMOUNT -> stringResource(R.string.import_reason_missing_amount)
    RowRejection.FRACTIONAL_AMOUNT ->
        stringResource(R.string.import_reason_fractional, detail.orEmpty())

    RowRejection.NON_POSITIVE_AMOUNT ->
        stringResource(R.string.import_reason_non_positive, detail.orEmpty())

    RowRejection.ZERO_ADJUSTMENT -> stringResource(R.string.import_reason_zero_adjustment)
    // A blank type cell and a misspelt one are different mistakes, and quoting an
    // empty string at the user reads as a bug.
    RowRejection.UNKNOWN_TYPE -> detail?.takeIf { it.isNotBlank() }
        ?.let { stringResource(R.string.import_reason_unknown_type, it) }
        ?: stringResource(R.string.import_reason_unknown_type_blank)

    RowRejection.MISSING_ACCOUNT -> stringResource(R.string.import_reason_missing_account)
    RowRejection.MISSING_CATEGORY -> stringResource(R.string.import_reason_missing_category)
    RowRejection.MISSING_DESTINATION -> stringResource(R.string.import_reason_missing_destination)
    RowRejection.DESTINATION_SAME_AS_SOURCE -> stringResource(R.string.import_reason_same_account)
    RowRejection.INBOUND_TRANSFER_LEG -> stringResource(R.string.import_reason_inbound_leg)
}

@Composable
fun ImportProblem.message(): String = when (this) {
    is ImportProblem.UnmappedAccount -> stringResource(R.string.import_problem_unmapped_account, name)
    is ImportProblem.UnmappedTransferDestination ->
        stringResource(R.string.import_problem_unmapped_transfer, categoryLabel())

    ImportProblem.NothingToImport -> stringResource(R.string.import_problem_nothing)
    is ImportProblem.TransferDestinationSameAsSource ->
        stringResource(R.string.import_problem_same_account, categoryLabel(), accountName)

    is ImportProblem.TransferPredatesOpening -> stringResource(
        R.string.import_problem_predates,
        accountName,
        openingDate.readable(),
        earliestTransfer.readable(),
    )
}

@Composable
private fun ImportProblem.categoryLabel(): String {
    val name = when (this) {
        is ImportProblem.UnmappedTransferDestination -> categoryName
        is ImportProblem.TransferDestinationSameAsSource -> categoryName
        else -> null
    }
    return name ?: stringResource(R.string.import_transfer_uncategorised)
}

@Composable
fun TransferDestinationMapping.label(): String = stringResource(
    R.string.import_transfer_group,
    rowCount,
    formatIdr(total),
)

@Composable
fun TransferDestinationMapping.title(): String =
    categoryName ?: stringResource(R.string.import_transfer_uncategorised)

/** Dates are shown as `17 Aug 2026` rather than a locale format that reorders the parts. */
fun LocalDate.readable(): String = "$dayOfMonth ${MONTHS[monthNumber - 1]} $year"

private val MONTHS = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)
