package com.dvnnfrr.saku.ui.importexport

import com.dvnnfrr.saku.domain.Account
import com.dvnnfrr.saku.domain.AccountType
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.excel.ImportTally
import com.dvnnfrr.saku.excel.LayoutMatch
import com.dvnnfrr.saku.excel.RejectedRow
import com.dvnnfrr.saku.excel.SheetLayout
import kotlinx.datetime.LocalDate

/**
 * Which menu row opened the screen. It changes the title and the sentence about what
 * was expected, and nothing else — the parser reads the header row either way, so
 * picking the wrong one costs the user a re-read rather than a bad import.
 */
enum class ImportEntryPoint {
    MONEY_MANAGER,
    EXCEL,
}

/** What a name in the file will be satisfied by, once the user has said. */
sealed interface AccountChoice {
    data class Existing(val id: Long, val name: String) : AccountChoice

    /**
     * A new account, created only when the import is confirmed. [type] is a guess from
     * the name; the accounts screen can change it afterwards, and the type affects only
     * which group the account is totalled under.
     */
    data class Create(val name: String, val type: AccountType) : AccountChoice

    val displayName: String
        get() = when (this) {
            is Existing -> name
            is Create -> name
        }
}

/**
 * One distinct account name in the file.
 *
 * [prefilled] separates a mapping the app guessed from one the user made. `Card` covers
 * 1,150 of the 1,200 reference rows, so it arrives pointing at BCA — but the row still
 * shows as a guess, because attributing a year of history to one bank account
 * is partly fiction and the user should see the guess rather than discover it later.
 */
data class AccountMapping(
    val sourceName: String,
    val rowCount: Int,
    val choice: AccountChoice?,
    val prefilled: Boolean,
)

/**
 * A group of transfers sharing a category and needing one destination between them.
 *
 * This is the single most consequential row on the screen. A transfer with no
 * destination debits its source and credits nobody, which is exactly how 5.050.800 of
 * investment money reads as spent in the source data.
 */
data class TransferDestinationMapping(
    val categoryName: String?,
    val rowCount: Int,
    val total: Money,
    val choice: AccountChoice?,
)

/** A category found in the file, and whether importing will create it. */
data class CategoryOutcome(val name: String, val rowCount: Int, val existingId: Long?) {
    val willBeCreated: Boolean get() = existingId == null
}

/** A row where the file's two amount columns disagree. The user decides whether it goes in. */
data class DiscrepancyRow(
    val sourceRow: Int,
    val idr: Money,
    val declared: String,
    val included: Boolean,
)

/** Earliest and latest transaction in the file, in the local zone. */
data class DateSpan(val first: LocalDate, val last: LocalDate)

/** Everything standing between the preview and a confirmed import. */
sealed interface ImportProblem {
    data class UnmappedAccount(val name: String) : ImportProblem

    data class UnmappedTransferDestination(val categoryName: String?) : ImportProblem

    data object NothingToImport : ImportProblem

    /** A transfer to the account it came from moves nothing and is refused downstream. */
    data class TransferDestinationSameAsSource(
        val categoryName: String?,
        val accountName: String,
    ) : ImportProblem

    /**
     * A transfer dated before one of its two accounts opened.
     *
     * The balance query only counts a transaction from an account's as-of date, so such
     * a transfer is debited from one side and never credited to the other — it destroys
     * the amount rather than moving it. The repository refuses the whole batch over one
     * of these, so the preview refuses first and says which date to change.
     */
    data class TransferPredatesOpening(
        val accountName: String,
        val openingDate: LocalDate,
        val earliestTransfer: LocalDate,
    ) : ImportProblem
}

/** Why a chosen file never reached the preview. */
sealed interface ReadFailure {
    data class UnrecognisedLayout(val mismatch: LayoutMatch.Unrecognised) : ReadFailure

    /** Not a workbook, or a workbook we could not open. [detail] is the library's word. */
    data class Unreadable(val detail: String?) : ReadFailure

    data object NoUsableRows : ReadFailure
}

/**
 * The preview. Holding it as one immutable value is what makes "writes nothing until
 * confirmed" checkable rather than asserted: every mapping the user makes replaces this
 * object, and nothing here touches a repository.
 */
data class ImportPreview(
    val entryPoint: ImportEntryPoint,
    val fileName: String,
    val layout: SheetLayout,
    val tally: ImportTally,
    val dateSpan: DateSpan?,
    val accounts: List<AccountMapping>,
    val transferDestinations: List<TransferDestinationMapping>,
    val categories: List<CategoryOutcome>,
    val discrepancies: List<DiscrepancyRow>,
    val heldBack: List<RejectedRow>,
    val problems: List<ImportProblem>,
    val availableAccounts: List<Account>,
    /** The tally is being recomputed after a mapping change. */
    val recounting: Boolean = false,
    val committing: Boolean = false,
    /** A message from the repository's row validator, shown instead of crashing. */
    val failure: String? = null,
) {
    val canImport: Boolean get() = problems.isEmpty() && !committing && !recounting

    val newCategoryCount: Int get() = categories.count { it.willBeCreated }
}

sealed interface ImportUiState {
    val entryPoint: ImportEntryPoint

    data class Idle(override val entryPoint: ImportEntryPoint) : ImportUiState

    data class Reading(
        override val entryPoint: ImportEntryPoint,
        val fileName: String,
    ) : ImportUiState

    data class Refused(
        override val entryPoint: ImportEntryPoint,
        val fileName: String,
        val reason: ReadFailure,
    ) : ImportUiState

    data class Ready(val preview: ImportPreview) : ImportUiState {
        override val entryPoint: ImportEntryPoint get() = preview.entryPoint
    }

    data class Imported(
        override val entryPoint: ImportEntryPoint,
        val fileName: String,
        val inserted: Int,
        val skipped: Int,
    ) : ImportUiState
}

/**
 * The type a newly created account gets. A guess from the name, because the alternative
 * is a second question in the middle of a mapping step, and the only thing the type
 * changes is which group the accounts screen totals it under.
 */
fun guessAccountType(name: String): AccountType {
    val lower = name.trim().lowercase()
    return when {
        CASH_WORDS.any { lower.contains(it) } -> AccountType.CASH
        INVESTMENT_WORDS.any { lower.contains(it) } -> AccountType.INVESTMENT
        LIABILITY_WORDS.any { lower.contains(it) } -> AccountType.LIABILITY
        else -> AccountType.BANK
    }
}

private val CASH_WORDS = listOf("cash", "tunai", "dompet", "wallet")
private val INVESTMENT_WORDS = listOf("rdpu", "saham", "reksa", "invest", "bibit", "emas")
private val LIABILITY_WORDS = listOf("kredit", "credit card", "kartu kredit", "hutang", "paylater")
