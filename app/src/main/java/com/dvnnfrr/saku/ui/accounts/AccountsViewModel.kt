package com.dvnnfrr.saku.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dvnnfrr.saku.data.repository.AccountRepository
import com.dvnnfrr.saku.data.repository.CategoryRepository
import com.dvnnfrr.saku.data.repository.TransactionRepository
import com.dvnnfrr.saku.domain.Account
import com.dvnnfrr.saku.domain.AccountType
import com.dvnnfrr.saku.domain.ColorSlot
import com.dvnnfrr.saku.domain.DateRange
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.sakuViewModelFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

/**
 * State for the accounts screen.
 *
 * Observes; never polls. Every figure on the screen is derived from
 * [AccountRepository.observeAll], which is a Room `Flow` over a query that reads both
 * the account and the transaction tables — so recording a transaction, reconciling or
 * editing an opening balance all repaint the balances without anything asking again.
 *
 * The ViewModel holds only what the user has chosen: which segment is selected and
 * which account is open. Everything else comes out of [AccountsAssembly], which is
 * pure.
 */
class AccountsViewModel(
    private val accounts: AccountRepository,
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
    private val clock: Clock = Clock.System,
) : ViewModel() {

    private data class Selection(
        val selectedAccountId: Long? = null,
        val detailId: Long? = null,
    )

    private val selection = MutableStateFlow(Selection())
    private val message = MutableStateFlow<AccountsMessage?>(null)

    private val live: Flow<List<Account>> = accounts.observeAll()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val detail: Flow<AccountDetailUiState?> =
        selection
            .map { it.detailId }
            .distinctUntilChanged()
            .flatMapLatest { id -> if (id == null) flowOf(null) else detailFor(id) }

    val state: StateFlow<AccountsUiState> =
        combine(live, detail, selection, message) { all, openDetail, current, note ->
            val now = clock.now()
            val shares = accountMix(all)
            val groups = accountGroups(all, shares, now, zone)
            val sharesById = shares.associate { it.accountId to it.percent }
            AccountsUiState(
                loading = false,
                summary = accountSummary(all),
                groups = groups,
                mix = shares,
                archived = all.filter { it.archived }
                    .map { it.toRowUi(sharesById[it.id], now, zone) },
                // Dropped the moment its account stops being chartable: archiving or
                // spending the balance to zero takes the segment away, and a selection
                // pointing at a segment nobody can see leaves the centre reading a
                // figure with no slice under it.
                selectedAccountId = current.selectedAccountId
                    ?.takeIf { id -> shares.any { it.accountId == id } },
                detail = openDetail,
                message = note,
            )
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = AccountsUiState(),
            )

    /**
     * The detail's rows.
     *
     * The query is keyed on the ledger range alone, so an ordinary balance change
     * re-reads the rows without tearing down and re-subscribing the query — the same
     * reason the transaction list keys its `flatMapLatest` on the month rather than on
     * the whole selection.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun detailFor(id: Long): Flow<AccountDetailUiState?> {
        val account = live.map { all -> all.firstOrNull { it.id == id } }.distinctUntilChanged()
        val rows = account
            .map { it?.let(::ledgerRange) }
            .distinctUntilChanged()
            .flatMapLatest { range ->
                if (range == null) flowOf(emptyList()) else transactions.observeInRange(range, zone)
            }
        return combine(account, rows, categories.observeAll()) { current, transactions, cats ->
            if (current == null) return@combine null
            val names = cats.associate { it.id to it.name }
            AccountDetailUiState(
                account = current.toRowUi(sharePercent = null, now = clock.now(), zone = zone),
                entries = accountLedger(current, transactions).map { it.toUi(names) },
            )
        }
    }

    /**
     * The span the detail lists.
     *
     * It starts at the cutover because nothing earlier moves this balance, and it has to
     * run far enough forward that a row cannot count towards the balance while missing
     * from the list that explains it — the balance query has no upper bound at all, and
     * a date entered by hand can be in the future.
     */
    private fun ledgerRange(account: Account): DateRange {
        val start = account.openingAsOf.toLocalDateTime(zone).date
        return DateRange.custom(start, LocalDate(start.year + LEDGER_HORIZON_YEARS, 12, 31))
    }

    private fun LedgerEntry.toUi(categoryNames: Map<Long, String>): LedgerEntryUi = LedgerEntryUi(
        transactionId = transaction.id,
        date = transaction.occurredAt.toLocalDateTime(zone).date,
        label = transaction.categoryId?.let { categoryNames[it] },
        note = transaction.note?.takeIf { it.isNotBlank() },
        type = transaction.type,
        effect = effect,
        runningBalance = runningBalance,
    )

    /**
     * A donut tap. [index] is an index into [AccountsUiState.mix]; null is a tap that
     * hit no segment, and tapping the selected one again lets go of it.
     */
    fun selectSegment(index: Int?) {
        val id = index?.let { state.value.accountIdForSegment(it) }
        selection.update {
            it.copy(selectedAccountId = if (it.selectedAccountId == id) null else id)
        }
    }

    fun openDetail(id: Long) = selection.update { it.copy(detailId = id) }

    fun closeDetail() = selection.update { it.copy(detailId = null) }

    fun dismissMessage() {
        message.value = null
    }

    /** A blank form for the create sheet, dated today. */
    fun newAccountForm(): AccountFormState =
        AccountFormState(openingAsOf = clock.now().toLocalDateTime(zone).date)

    fun editFormFor(id: Long): AccountFormState? {
        val account = (state.value.rows + state.value.archived).firstOrNull { it.id == id }
            ?: return null
        return AccountFormState(
            id = account.id,
            name = account.name,
            type = account.type,
            colorSlot = account.colorSlot,
            openingBalance = account.openingBalance,
            openingAsOf = account.openingAsOf,
        )
    }

    fun create(form: AccountFormState) = mutate {
        accounts.create(
            name = form.name,
            type = form.type,
            openingBalance = form.openingBalance,
            openingAsOf = form.openingAsOf.atStartOfDayIn(zone),
            colorSlot = form.colorSlot,
        )
    }

    /**
     * Saves an edit field by field, because the repository exposes it that way — a
     * whole-row write would put back whatever the form last read for the columns the
     * user did not touch, and a rename racing a reconcile would move the balance.
     *
     * The opening balance goes last and can be refused: `setOpening` blocks a cutover
     * that would leave a transfer counted on one leg and not the other. Ordering it
     * last means a refusal costs the user the rename, not the whole edit.
     */
    fun save(form: AccountFormState) {
        val id = form.id ?: return
        mutate {
            accounts.rename(id, form.name)
            accounts.retype(id, form.type)
            form.colorSlot?.let { accounts.recolor(id, it) }
            accounts.setOpening(
                id = id,
                openingBalance = form.openingBalance,
                openingAsOf = form.openingAsOf.atStartOfDayIn(zone),
            )
        }
    }

    /**
     * Archive, never delete. The account keeps every transaction it is named on and
     * drops out of the pickers and the figures; unarchiving puts it back.
     */
    fun setArchived(id: Long, archived: Boolean) = mutate {
        accounts.setArchived(id, archived)
        if (archived && selection.value.detailId == id) {
            selection.update { it.copy(detailId = null) }
        }
    }

    /**
     * Writes the difference between the derived balance and what the user says is really
     * there as a visible ADJUSTMENT. Nothing is overwritten: the size of these, on Cash
     * especially, is the measurement of how much spending goes untracked.
     */
    fun reconcile(id: Long, trueBalance: Money) = mutate {
        val before = accounts.byId(id) ?: return@mutate
        // Read before the write, because the write is what makes them equal.
        val difference = adjustmentFor(before.balance, trueBalance)
        accounts.reconcile(id, trueBalance, clock.now())
        message.value = AccountsMessage.Reconciled(
            accountName = before.name,
            adjustment = difference.takeUnless { it.isZero },
        )
    }

    /**
     * Runs a write and turns a refusal into something the screen can say.
     *
     * The repository guards money with `require` and `check` — an opening date that
     * would split a transfer, a blank name, an account that still holds history. Those
     * are user-facing outcomes, not defects, and letting them escape the coroutine would
     * take the process down over a mistyped date.
     */
    private fun mutate(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (refusal: IllegalArgumentException) {
                message.value = AccountsMessage.Refused(refusal.message.orEmpty())
            } catch (refusal: IllegalStateException) {
                message.value = AccountsMessage.Refused(refusal.message.orEmpty())
            }
        }
    }

    companion object {
        /**
         * How far past the cutover the detail lists. A century is past any date a user
         * enters by hand and keeps the query bounded and indexed.
         */
        private const val LEDGER_HORIZON_YEARS = 100

        val Factory = sakuViewModelFactory {
            AccountsViewModel(accounts, transactions, categories)
        }
    }
}

/** Colour slots offered by the editor, in palette order. */
val COLOR_SLOTS: List<ColorSlot> = (0 until ColorSlot.COUNT).map(::ColorSlot)

/** The four types the editor offers, in the order the selector shows them. */
val ACCOUNT_TYPES: List<AccountType> = AccountType.entries.toList()
