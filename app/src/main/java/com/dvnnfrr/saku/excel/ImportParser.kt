package com.dvnnfrr.saku.excel

import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.TxnType
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toKotlinLocalDateTime
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * One row of a sheet, typed and validated but not yet mapped onto any database id.
 *
 * Names rather than ids, deliberately: the file knows `Card` and `Saham`, not account
 * 3 and category 12, and the mapping from one to the other is the user's to make at
 * the preview. Keeping the parser id-free is also what lets it be tested against the
 * real export with no database in the room.
 */
data class ParsedRow(
    /** The workbook's own row number, so the preview can point at a row the user can find. */
    val sourceRow: Int,
    val occurredAt: Instant,
    val type: TxnType,
    val amount: Money,
    val accountName: String,
    /** Present only in the Saku layout; Money Manager transfers arrive without one. */
    val toAccountName: String?,
    val categoryName: String?,
    val subcategoryName: String?,
    val note: String?,
    val discrepancy: AmountDiscrepancy?,
)

/**
 * The Money Manager export stores the figure twice — column 6 as a number, column 9 as
 * text. They agree on all 1,200 reference rows. Where they do not, the row is still
 * parsed but flagged, because only the user can say which column was right.
 */
data class AmountDiscrepancy(val idr: BigDecimal, val declared: String)

/** Why a row never became a [ParsedRow]. Rendered into a sentence by the UI. */
enum class RowRejection {
    NON_IDR_CURRENCY,
    MISSING_DATE,
    MISSING_AMOUNT,
    FRACTIONAL_AMOUNT,
    NON_POSITIVE_AMOUNT,
    ZERO_ADJUSTMENT,
    UNKNOWN_TYPE,
    MISSING_ACCOUNT,
    MISSING_CATEGORY,
    MISSING_DESTINATION,
    DESTINATION_SAME_AS_SOURCE,
    INBOUND_TRANSFER_LEG,
}

/** [detail] is whatever the file held — a currency code, a type word — never prose. */
data class RejectedRow(val sourceRow: Int, val reason: RowRejection, val detail: String? = null)

data class ParsedSheet(
    val layout: SheetLayout,
    val sheetName: String,
    /** Data rows in the file, including the ones that were rejected. */
    val fileRowCount: Int,
    val rows: List<ParsedRow>,
    val rejected: List<RejectedRow>,
) {
    val accountNames: List<String> get() = rows.map { it.accountName }.distinct().sorted()

    /** Destination names the file supplied itself. Empty for a Money Manager export. */
    val destinationNames: List<String>
        get() = rows.mapNotNull { it.toAccountName }.distinct().sorted()

    val categoryNames: List<String> get() = rows.mapNotNull { it.categoryName }.distinct().sorted()

    /**
     * Transfers that arrived with no destination, grouped by the category that stood in
     * for one. This is the group the preview has to resolve before anything is written:
     * a transfer with no destination debits its source and credits nobody, which is the
     * 5.050.800 of investment money the reference data reads as simply gone.
     */
    val transferGroupsNeedingDestination: List<TransferGroup>
        get() = rows.filter { it.type == TxnType.TRANSFER && it.toAccountName == null }
            .groupBy { it.categoryName }
            .map { (category, group) ->
                TransferGroup(
                    categoryName = category,
                    rowCount = group.size,
                    total = Money(group.sumOf { it.amount.rupiah }),
                )
            }
            .sortedBy { it.categoryName ?: "" }

    val earliest: Instant? get() = rows.minOfOrNull { it.occurredAt }

    val latest: Instant? get() = rows.maxOfOrNull { it.occurredAt }
}

/** [categoryName] is null when the transfer rows carried no category either. */
data class TransferGroup(val categoryName: String?, val rowCount: Int, val total: Money)

sealed interface ParseOutcome {
    data class Parsed(val sheet: ParsedSheet) : ParseOutcome

    data class UnrecognisedLayout(val mismatch: LayoutMatch.Unrecognised) : ParseOutcome
}

object ImportParser {

    /**
     * Layout is decided before a single data row is touched, so an unrecognised file is
     * refused rather than half-parsed into nonsense.
     */
    fun parse(table: SheetTable, zone: TimeZone): ParseOutcome {
        val sheet = LayoutDetector.withoutMoneyManagerTag(table)
        return when (val match = LayoutDetector.detect(sheet.headers)) {
            is LayoutMatch.Unrecognised -> ParseOutcome.UnrecognisedLayout(match)
            is LayoutMatch.Recognised -> ParseOutcome.Parsed(parse(match.layout, sheet, zone))
        }
    }

    fun parse(layout: SheetLayout, table: SheetTable, zone: TimeZone): ParsedSheet {
        val rows = mutableListOf<ParsedRow>()
        val rejected = mutableListOf<RejectedRow>()

        table.rows.forEach { row ->
            when (layout) {
                SheetLayout.MONEY_MANAGER -> parseMoneyManager(row, zone)
                SheetLayout.SAKU -> parseSaku(row, zone)
            }.let { outcome ->
                when (outcome) {
                    is RowOutcome.Ok -> rows += outcome.row
                    is RowOutcome.Rejected -> rejected += outcome.rejection
                    RowOutcome.Blank -> Unit
                }
            }
        }

        return ParsedSheet(
            layout = layout,
            sheetName = table.sheetName,
            fileRowCount = table.rows.count { it.cells.any { cell -> cell != CellValue.Empty } },
            rows = rows,
            rejected = rejected,
        )
    }

    private sealed interface RowOutcome {
        data class Ok(val row: ParsedRow) : RowOutcome

        data class Rejected(val rejection: RejectedRow) : RowOutcome

        /** An entirely empty row. Trailing blanks are common and are not an error. */
        data object Blank : RowOutcome
    }

    private fun parseMoneyManager(row: SheetRow, zone: TimeZone): RowOutcome {
        val columns = SheetLayout.MoneyManager
        if (row.cells.all { it == CellValue.Empty }) return RowOutcome.Blank

        // Currency first. A non-IDR row is held back before anything else is read,
        // because the app stores one currency and has no rate to convert with — and
        // inventing one is the failure mode this check exists to make impossible.
        val currency = row.textAt(columns.CURRENCY)?.trim()
        if (currency != null && !currency.equals(IDR, ignoreCase = true)) {
            return reject(row, RowRejection.NON_IDR_CURRENCY, currency)
        }

        val occurredAt = row.timestampAt(columns.DATE)
            ?: return reject(row, RowRejection.MISSING_DATE)

        val rawType = row.textAt(columns.TYPE)?.trim()
        val type = when (val token = rawType?.let { parseType(it) }) {
            null -> return reject(row, RowRejection.UNKNOWN_TYPE, rawType)
            // Money Manager writes a transfer as two legs. Importing both would move the
            // money twice; the out leg alone carries source, amount and category, so the
            // in leg is the one to drop.
            TypeToken.InboundTransfer -> return reject(row, RowRejection.INBOUND_TRANSFER_LEG, rawType)
            is TypeToken.Known -> token.type
        }

        val account = row.textAt(columns.ACCOUNT)?.trim()?.takeIf { it.isNotEmpty() }
            ?: return reject(row, RowRejection.MISSING_ACCOUNT)

        val idr = row.numberAt(columns.IDR)
            ?: return reject(row, RowRejection.MISSING_AMOUNT)
        val amount = idr.toWholeRupiah() ?: return reject(row, RowRejection.FRACTIONAL_AMOUNT, idr.toPlainString())
        if (!amount.isPositive) {
            return reject(row, RowRejection.NON_POSITIVE_AMOUNT, idr.toPlainString())
        }

        val category = row.cleanText(columns.CATEGORY)
        if (type.requiresCategory && category == null) {
            return reject(row, RowRejection.MISSING_CATEGORY)
        }

        // Column 11 is read only to be ignored; the account comes from column 2 alone.
        return RowOutcome.Ok(
            ParsedRow(
                sourceRow = row.number,
                occurredAt = occurredAt.toInstantAtSecond(zone),
                type = type,
                amount = amount,
                accountName = account,
                toAccountName = null,
                categoryName = category,
                subcategoryName = row.cleanText(columns.SUBCATEGORY),
                note = row.cleanText(columns.NOTE),
                discrepancy = crossCheck(idr, row[columns.AMOUNT]),
            ),
        )
    }

    private fun parseSaku(row: SheetRow, zone: TimeZone): RowOutcome {
        val columns = SheetLayout.Saku
        if (row.cells.all { it == CellValue.Empty }) return RowOutcome.Blank

        val occurredAt = row.timestampAt(columns.DATE)
            ?: return reject(row, RowRejection.MISSING_DATE)

        val rawType = row.textAt(columns.TYPE)?.trim()
        // Saku never writes an in leg, so one here means the file is not ours.
        val type = (rawType?.let { parseType(it) } as? TypeToken.Known)?.type
            ?: return reject(row, RowRejection.UNKNOWN_TYPE, rawType)

        val account = row.textAt(columns.ACCOUNT)?.trim()?.takeIf { it.isNotEmpty() }
            ?: return reject(row, RowRejection.MISSING_ACCOUNT)

        val figure = row.numberAt(columns.AMOUNT)
            ?: return reject(row, RowRejection.MISSING_AMOUNT)
        val amount = figure.toWholeRupiah()
            ?: return reject(row, RowRejection.FRACTIONAL_AMOUNT, figure.toPlainString())

        // Only an adjustment may be signed; for the other three the direction comes from
        // the type. A negative expense would ADD to the balance, so it is refused here
        // rather than left for the repository to throw on halfway through a batch.
        if (type == TxnType.ADJUSTMENT) {
            if (amount.isZero) return reject(row, RowRejection.ZERO_ADJUSTMENT)
        } else if (!amount.isPositive) {
            return reject(row, RowRejection.NON_POSITIVE_AMOUNT, figure.toPlainString())
        }

        val destination = row.cleanText(columns.TO_ACCOUNT)
        if (type == TxnType.TRANSFER) {
            if (destination == null) return reject(row, RowRejection.MISSING_DESTINATION)
            if (destination.equals(account, ignoreCase = true)) {
                return reject(row, RowRejection.DESTINATION_SAME_AS_SOURCE, destination)
            }
        }

        val category = row.cleanText(columns.CATEGORY)
        // The repository refuses a categoryless expense and aborts the whole batch over
        // it, so the row is held back here where it can be listed rather than there.
        if (type.requiresCategory && category == null) {
            return reject(row, RowRejection.MISSING_CATEGORY)
        }

        return RowOutcome.Ok(
            ParsedRow(
                sourceRow = row.number,
                occurredAt = occurredAt.toInstantAtSecond(zone),
                type = type,
                amount = amount,
                accountName = account,
                toAccountName = destination.takeIf { type == TxnType.TRANSFER },
                categoryName = category,
                subcategoryName = row.cleanText(columns.SUBCATEGORY),
                note = row.cleanText(columns.NOTE),
                discrepancy = null,
            ),
        )
    }

    private fun reject(row: SheetRow, reason: RowRejection, detail: String? = null) =
        RowOutcome.Rejected(RejectedRow(row.number, reason, detail))

    private sealed interface TypeToken {
        data class Known(val type: TxnType) : TypeToken

        /** Has no Saku equivalent: it must be dropped rather than mapped. */
        data object InboundTransfer : TypeToken
    }

    /**
     * The type words the two layouts use. `Transfer-Out` is Money Manager's, `Transfer`
     * is ours, and both mean one movement between two of the owner's own accounts.
     */
    private fun parseType(raw: String): TypeToken? = when (raw.trim().lowercase()) {
        "expense", "expenses" -> TypeToken.Known(TxnType.EXPENSE)
        "income" -> TypeToken.Known(TxnType.INCOME)
        "transfer", "transfer-out", "transfer out" -> TypeToken.Known(TxnType.TRANSFER)
        "adjustment" -> TypeToken.Known(TxnType.ADJUSTMENT)
        "transfer-in", "transfer in" -> TypeToken.InboundTransfer
        else -> null
    }

    /**
     * Whole rupiah or nothing. The app has no subunit, so a fractional figure is a sign
     * the file is not what it claims rather than something to round away silently.
     */
    private fun BigDecimal.toWholeRupiah(): Money? {
        val trimmed = stripTrailingZeros()
        if (trimmed.scale() > 0) return null
        return runCatching { Money(trimmed.longValueExact()) }.getOrNull()
    }

    private fun crossCheck(idr: BigDecimal, declared: CellValue): AmountDiscrepancy? {
        val text = when (declared) {
            is CellValue.Text -> declared.value.trim()
            is CellValue.Number -> declared.value.toPlainString()
            else -> return null
        }
        if (text.isEmpty()) return null
        // Parsed as BigDecimal, never as a double: "187000.0" and 187000 must compare
        // equal, and 1_200 doubles is exactly where a rounding error hides.
        val parsed = runCatching { BigDecimal(text) }.getOrNull()
            ?: return AmountDiscrepancy(idr, text)
        return if (parsed.compareTo(idr) == 0) null else AmountDiscrepancy(idr, text)
    }

    private fun SheetRow.cleanText(index: Int): String? = when (val cell = this[index]) {
        is CellValue.Text -> cell.value.trim().takeIf { it.isNotEmpty() }
        is CellValue.Number -> cell.value.toPlainString()
        else -> null
    }

    private const val IDR = "IDR"
}

/**
 * Excel timestamp to an [Instant] on a whole second.
 *
 * Two things happen here. Sub-second precision is dropped, because the reference
 * export's microseconds record when the row was *typed in* and the dedup key is
 * defined to the second. And a value a hair under a whole second is snapped up first:
 * Excel keeps a timestamp as a fractional day in a double, so 18:47:34 written by
 * [XlsxWriter] can read back as 18:47:33.9999998. Truncating that loses a second, the
 * row's dedup hash changes, and re-importing our own export reports every row as new
 * instead of every row as already present.
 */
internal fun LocalDateTime.toInstantAtSecond(zone: TimeZone): Instant {
    val snapped = if (nano >= NEAR_SECOND_NANOS) plusSeconds(1) else this
    return snapped.truncatedTo(ChronoUnit.SECONDS).toKotlinLocalDateTime().toInstant(zone)
}

/** One millisecond. Excel's serial-date error is orders of magnitude below this. */
private const val NEAR_SECOND_NANOS = 999_000_000
