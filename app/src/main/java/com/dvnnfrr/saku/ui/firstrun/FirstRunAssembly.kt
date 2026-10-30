package com.dvnnfrr.saku.ui.firstrun

import com.dvnnfrr.saku.domain.Account
import com.dvnnfrr.saku.domain.AccountType
import com.dvnnfrr.saku.domain.Category
import com.dvnnfrr.saku.domain.CategoryKind
import com.dvnnfrr.saku.domain.Money
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

/*
 * Pure assembly for first-run setup. No Compose, no Android, no coroutines: when setup is
 * offered, which accounts it lists, what counts as a valid answer, and exactly which
 * repository writes Done makes are all decided here and tested against plain lists.
 * The ViewModels only hold the user's choices and run the plan.
 */

/**
 * Setup is for an install nobody has used yet. Any transaction at all, typed, imported,
 * restored or a reconcile adjustment, means the ledger already has history, and the
 * flag means the user has already answered once.
 */
fun shouldOfferFirstRun(completed: Boolean, ledgerHasRows: Boolean): Boolean =
    !completed && !ledgerHasRows

/**
 * The as-of date every account written by setup shares: the start of the current day in
 * [zone]. One cutover for all of them, so a transfer can never fall between two accounts'
 * as-of dates; and the start of the day, as the account editor stores it, so something
 * spent this morning and logged tonight still moves the balance.
 */
fun startOfToday(now: Instant, zone: TimeZone): Instant =
    now.toLocalDateTime(zone).date.atStartOfDayIn(zone)

fun today(now: Instant, zone: TimeZone): LocalDate = now.toLocalDateTime(zone).date

// ---- Accounts step -------------------------------------------------------------------

/**
 * One row of the accounts step: what the user has typed so far.
 *
 * [amountDigits] is the field's text, digits only. A liability's field asks how much is
 * owed and takes a positive figure; [opening] applies the sign.
 */
data class AccountDraft(
    /** Stable across edits, for list keys and for removing a row. */
    val key: String,
    /** Null for an account added during setup. */
    val accountId: Long?,
    val name: String,
    val type: AccountType,
    val amountDigits: String = "",
) {
    val isAdded: Boolean get() = accountId == null

    /** What the user says is in it, or owed on it, today. Empty means zero. */
    val amount: Money get() = Money(amountDigits.toLongOrNull() ?: 0L)

    /** The stored opening balance: negative for a liability, which is owed money. */
    val opening: Money get() = openingFor(type, amount)
}

/** A liability's balance is negative while money is owed; the summary depends on it. */
fun openingFor(type: AccountType, amount: Money): Money =
    if (type == AccountType.LIABILITY) -amount.absolute else amount.absolute

/**
 * The rows setup starts from: every live account, in the order the accounts screen lists
 * them. An account that already carries an opening balance shows it, so a setup that was
 * interrupted after its writes opens on what was written rather than on blanks.
 */
fun accountDraftsFrom(accounts: List<Account>): List<AccountDraft> =
    accounts
        .filterNot { it.archived }
        .sortedWith(compareBy({ it.sortOrder }, { it.id }))
        .map { account ->
            AccountDraft(
                key = existingKey(account.id),
                accountId = account.id,
                name = account.name,
                type = account.type,
                amountDigits = account.openingBalance.absolute.rupiah
                    .takeIf { it != 0L }?.toString().orEmpty(),
            )
        }

fun existingKey(id: Long): String = "account-$id"

fun addedKey(serial: Int): String = "added-$serial"

/** Long is 19 digits; 15 keeps the largest balance at 999 trillion rupiah, as the editor does. */
private const val MAX_RUPIAH_DIGITS = 15

/**
 * What the amount field keeps of a keystroke: digits only, no leading zero, at most 15.
 * The sign comes from the account type, never from the keyboard.
 */
fun amountDigitsFrom(raw: String): String =
    raw.filter { it.isDigit() }.trimStart('0').take(MAX_RUPIAH_DIGITS)

enum class DraftProblem {
    NAME_MISSING,

    /** Another row, or an archived account, already has this name. */
    NAME_TAKEN,
}

/** The comparison `AccountDao.findAnyByName` makes: case and surrounding whitespace ignored. */
private fun nameKey(name: String): String = name.trim().lowercase()

/**
 * Problems by row key. The first row holding a name keeps it; later rows with the same
 * name are the ones flagged, so renaming the second row is what clears it. Archived
 * names count too: `findOrCreate` would otherwise map a new row onto the archived account.
 */
fun validateAccountDrafts(
    drafts: List<AccountDraft>,
    archivedNames: List<String>,
): Map<String, DraftProblem> {
    val taken = archivedNames.map(::nameKey).toMutableSet()
    val problems = mutableMapOf<String, DraftProblem>()
    drafts.forEach { draft ->
        val key = nameKey(draft.name)
        when {
            key.isEmpty() -> problems[draft.key] = DraftProblem.NAME_MISSING
            key in taken -> problems[draft.key] = DraftProblem.NAME_TAKEN
            else -> taken += key
        }
    }
    return problems
}

/** One account's share of what Done writes. */
sealed interface AccountWrite {
    /** An account that exists: rename and retype only when changed, then the opening. */
    data class Update(
        val accountId: Long,
        val rename: String?,
        val retype: AccountType?,
        val opening: Money,
    ) : AccountWrite

    /** An account added in setup, created through `findOrCreate` so a retry is harmless. */
    data class Add(val name: String, val type: AccountType, val opening: Money) : AccountWrite
}

/**
 * Exactly the writes Done makes, in row order. Every row gets its opening balance, zero
 * included: zero is an answer, and it moves the account's as-of date to today with the
 * others. A row whose account has vanished meanwhile is dropped rather than recreated.
 */
fun planAccountWrites(drafts: List<AccountDraft>, current: List<Account>): List<AccountWrite> {
    val byId = current.associateBy { it.id }
    return drafts.mapNotNull { draft ->
        val name = draft.name.trim()
        if (draft.accountId == null) return@mapNotNull AccountWrite.Add(name, draft.type, draft.opening)
        val account = byId[draft.accountId] ?: return@mapNotNull null
        AccountWrite.Update(
            accountId = account.id,
            rename = name.takeIf { it != account.name },
            retype = draft.type.takeIf { it != account.type },
            opening = draft.opening,
        )
    }
}

// ---- Categories step -----------------------------------------------------------------

/** A category the user can tick: a suggestion, or a name they typed. */
data class CategoryChoice(val name: String, val kind: CategoryKind)

/**
 * Generic suggestions, none ticked. Data rather than copy, like `FirstRunSeed`'s account
 * names: a ticked one becomes a stored category name. Since 30 Oct 2026 first run is not
 * tailored to the first owner, so these are deliberately plain.
 */
val FIRST_RUN_CATEGORY_SUGGESTIONS: List<CategoryChoice> = listOf(
    CategoryChoice("Food", CategoryKind.EXPENSE),
    CategoryChoice("Groceries", CategoryKind.EXPENSE),
    CategoryChoice("Transport", CategoryKind.EXPENSE),
    CategoryChoice("Bills", CategoryKind.EXPENSE),
    CategoryChoice("Shopping", CategoryKind.EXPENSE),
    CategoryChoice("Health", CategoryKind.EXPENSE),
    CategoryChoice("Fun", CategoryKind.EXPENSE),
    CategoryChoice("Salary", CategoryKind.INCOME),
    CategoryChoice("Gifts", CategoryKind.INCOME),
)

/** What the user has ticked and typed on the categories step. */
data class CategoryPicks(
    val ticked: Set<CategoryChoice> = emptySet(),
    /** Names the user typed, in the order added. Ticked when added. */
    val custom: List<CategoryChoice> = emptyList(),
) {
    fun toggle(choice: CategoryChoice): CategoryPicks =
        copy(ticked = if (choice in ticked) ticked - choice else ticked + choice)

    /**
     * Adds a typed name, ticked. A name matching a suggestion or an earlier one of the
     * same kind ticks that one instead of listing it twice. A blank name changes nothing.
     */
    fun add(name: String, kind: CategoryKind): CategoryPicks {
        val clean = name.trim()
        if (clean.isEmpty()) return this
        val match = (FIRST_RUN_CATEGORY_SUGGESTIONS + custom)
            .firstOrNull { it.kind == kind && nameKey(it.name) == nameKey(clean) }
        if (match != null) return copy(ticked = ticked + match)
        val added = CategoryChoice(clean, kind)
        return copy(ticked = ticked + added, custom = custom + added)
    }

    /** Every option on screen for [kind]: suggestions first, then the user's own. */
    fun options(kind: CategoryKind): List<CategoryChoice> =
        (FIRST_RUN_CATEGORY_SUGGESTIONS + custom).filter { it.kind == kind }
}

/** One tick row on the categories step. */
data class CategoryOptionUi(
    val choice: CategoryChoice,
    val ticked: Boolean,
    /** A live category with this name and kind exists already; shown ticked and fixed. */
    val exists: Boolean,
)

fun categoryOptions(
    picks: CategoryPicks,
    kind: CategoryKind,
    existing: List<Category>,
): List<CategoryOptionUi> {
    val live = liveCategoryKeys(existing)
    return picks.options(kind).map { choice ->
        val exists = (choice.kind to nameKey(choice.name)) in live
        CategoryOptionUi(choice = choice, ticked = exists || choice in picks.ticked, exists = exists)
    }
}

/**
 * The categories Done creates: ticked, de-duplicated per kind ignoring case and
 * whitespace, and minus any live category of the same name and kind. An archived one is
 * left to `findOrCreate`, which brings it back rather than making a twin.
 */
fun planCategoryWrites(picks: CategoryPicks, existing: List<Category>): List<CategoryChoice> {
    val seen = liveCategoryKeys(existing).toMutableSet()
    return (FIRST_RUN_CATEGORY_SUGGESTIONS + picks.custom)
        .filter { it in picks.ticked }
        .mapNotNull { choice ->
            val clean = choice.copy(name = choice.name.trim())
            val key = clean.kind to nameKey(clean.name)
            if (clean.name.isEmpty() || !seen.add(key)) null else clean
        }
}

private fun liveCategoryKeys(existing: List<Category>): Set<Pair<CategoryKind, String>> =
    existing.filterNot { it.archived }.map { it.kind to nameKey(it.name) }.toSet()

// ---- Screen state --------------------------------------------------------------------

enum class SetupStep { ACCOUNTS, CATEGORIES }

data class AccountDraftUi(val draft: AccountDraft, val problem: DraftProblem?)

/** Everything the manual setup renders. */
data class FirstRunSetupUiState(
    val step: SetupStep = SetupStep.ACCOUNTS,
    val today: LocalDate,
    val accounts: List<AccountDraftUi> = emptyList(),
    val expense: List<CategoryOptionUi> = emptyList(),
    val income: List<CategoryOptionUi> = emptyList(),
    val saving: Boolean = false,
    /** The repository's own words when a write was refused. */
    val refusal: String? = null,
    /** Every write landed; the host leaves setup. */
    val finished: Boolean = false,
) {
    val hasProblems: Boolean get() = accounts.any { it.problem != null }
}

/**
 * Folds the user's choices and the database into the screen. Problems show only after
 * the user has tried to move on, so an untouched blank row is not red the moment it is
 * added.
 */
fun assembleSetup(
    step: SetupStep,
    drafts: List<AccountDraft>,
    showProblems: Boolean,
    accounts: List<Account>,
    picks: CategoryPicks,
    categories: List<Category>,
    today: LocalDate,
    saving: Boolean,
    refusal: String?,
    finished: Boolean,
): FirstRunSetupUiState {
    val problems = if (showProblems) validateAccountDrafts(drafts, archivedNames(accounts)) else emptyMap()
    return FirstRunSetupUiState(
        step = step,
        today = today,
        accounts = drafts.map { AccountDraftUi(it, problems[it.key]) },
        expense = categoryOptions(picks, CategoryKind.EXPENSE, categories),
        income = categoryOptions(picks, CategoryKind.INCOME, categories),
        saving = saving,
        refusal = refusal,
        finished = finished,
    )
}

fun archivedNames(accounts: List<Account>): List<String> =
    accounts.filter { it.archived }.map { it.name }
