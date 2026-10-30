package com.dvnnfrr.saku.ui.entry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dvnnfrr.saku.data.repository.AccountRepository
import com.dvnnfrr.saku.data.repository.CategoryRepository
import com.dvnnfrr.saku.data.repository.DuplicateNameException
import com.dvnnfrr.saku.data.repository.TransactionRepository
import com.dvnnfrr.saku.domain.Account
import com.dvnnfrr.saku.domain.Category
import com.dvnnfrr.saku.domain.CategoryKind
import com.dvnnfrr.saku.domain.Transaction
import com.dvnnfrr.saku.domain.TxnType
import com.dvnnfrr.saku.sakuViewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

data class EntryUiState(
    val form: EntryFormState,
    val categories: List<Category> = emptyList(),
    val accounts: List<Account> = emptyList(),
    /**
     * Problems stay hidden until the first save attempt. Marking every empty field
     * red the moment the sheet opens is noise — nothing is wrong yet, the user has
     * simply not finished.
     */
    val showProblems: Boolean = false,
    val duplicateName: String? = null,
    val saved: Boolean = false,
) {
    val problems: List<EntryProblem> get() = if (showProblems) form.problems() else emptyList()

    fun problemFor(field: EntryField): EntryProblem? = problems.firstOrNull { it.field == field }
}

class TransactionEntryViewModel(
    private val transactions: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository,
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
    private val clock: Clock = Clock.System,
) : ViewModel() {

    private val _state = MutableStateFlow(
        EntryUiState(form = EntryFormState.recording(now())),
    )
    val state: StateFlow<EntryUiState> = _state.asStateFlow()

    private fun now(): LocalDateTime = clock.now().toLocalDateTime(zone)

    /** What [open] last loaded, so re-composition does not throw away typed input. */
    private var openedFor: Long? = null
    private var opened = false

    /**
     * Opens the sheet. [editingId] null records a new transaction.
     *
     * Accounts load before the form is seeded so a new entry opens on a real account
     * rather than on none, which would fail validation on a field the user never
     * touched.
     */
    fun open(editingId: Long? = null) {
        // Cleared synchronously, before the launch. The ViewModel is scoped to the
        // navigation entry, not the sheet, so `saved` outlived the sheet closing —
        // and the next open raced its own LaunchedEffect, which read the stale flag
        // and dismissed the sheet in the same frame it appeared.
        _state.update { it.copy(saved = false) }

        // Re-composition — rotation, or leaving and returning to the tab — re-runs the
        // effect that calls this. Reloading would discard everything typed so far,
        // even though the ViewModel survived and still holds it.
        if (opened && openedFor == editingId) return
        opened = true
        openedFor = editingId

        viewModelScope.launch {
            val accounts = accountRepository.observeActive().first()
            val existing = editingId?.let { transactions.byId(it) }
            val form = when (existing) {
                null -> EntryFormState.recording(now(), accountId = accounts.firstOrNull()?.id)
                else -> EntryFormState.editing(
                    existing,
                    existing.occurredAt.toLocalDateTime(zone),
                )
            }
            _state.value = EntryUiState(form = form, accounts = accounts)
            refreshCategories(form.categoryKind)
        }
    }

    private suspend fun refreshCategories(kind: CategoryKind?) {
        val list = kind?.let { categoryRepository.observeActiveByKind(it).first() }.orEmpty()
        _state.update { it.copy(categories = list) }
    }

    fun setType(type: TxnType) {
        val next = _state.updateAndGetForm { it.withType(type) }
        viewModelScope.launch { refreshCategories(next.categoryKind) }
    }

    fun setAmount(amount: AmountInput) = updateForm { it.copy(amount = amount) }

    /*
     * Key handlers reduce from the current state rather than from an AmountInput the
     * composable captured. Two keys landing in one frame both reduced from the same
     * snapshot, so the second overwrote the first and "18" came out as "8".
     */

    fun onDigit(digit: Int) = updateForm { it.copy(amount = it.amount.digit(digit)) }

    fun onTripleZero() = updateForm { it.copy(amount = it.amount.tripleZero()) }

    fun onBackspace() = updateForm { it.copy(amount = it.amount.backspace()) }

    fun onOperator(operator: AmountOperator) =
        updateForm { it.copy(amount = it.amount.operator(operator)) }

    fun setOccurredAt(at: LocalDateTime) = updateForm { it.copy(occurredAt = at) }

    fun setNote(note: String) = updateForm { it.copy(note = note) }

    fun setAccount(id: Long) = updateForm { it.copy(accountId = id) }

    fun setDestination(id: Long) = updateForm { it.copy(toAccountId = id) }

    fun selectCategory(id: Long?) = updateForm { it.copy(categoryId = id) }

    fun createCategory(name: String) {
        val kind = _state.value.form.categoryKind ?: return
        viewModelScope.launch {
            try {
                val id = categoryRepository.createCategory(name, kind)
                updateForm { it.copy(categoryId = id) }
                refreshCategories(kind)
            } catch (clash: DuplicateNameException) {
                // Offer the existing one rather than refusing outright: the user's
                // intent was to file under that name, and it already exists.
                updateForm { it.copy(categoryId = clash.existingId) }
                refreshCategories(kind)
                _state.update { it.copy(duplicateName = clash.name) }
            }
        }
    }

    fun dismissDuplicateNotice() = _state.update { it.copy(duplicateName = null) }

    fun save() {
        val form = _state.value.form
        if (!form.canSave) {
            _state.update { it.copy(showProblems = true) }
            return
        }
        viewModelScope.launch {
            val occurredAt = form.occurredAt.toInstant(zone)
            if (form.id == null) {
                transactions.record(
                    occurredAt = occurredAt,
                    type = form.type,
                    amount = form.money,
                    accountId = requireNotNull(form.accountId),
                    toAccountId = form.toAccountId,
                    categoryId = form.categoryId,
                    note = form.note.takeIf { it.isNotBlank() },
                )
            } else {
                val original = requireNotNull(transactions.byId(form.id))
                transactions.update(form.toTransaction(original, occurredAt))
            }
            _state.update { it.copy(saved = true) }
            // The next open is a different transaction, so let it reload.
            opened = false
        }
    }

    fun delete() {
        val id = _state.value.form.id ?: return
        viewModelScope.launch {
            transactions.delete(id)
            _state.update { it.copy(saved = true) }
            opened = false
        }
    }

    private fun updateForm(transform: (EntryFormState) -> EntryFormState) {
        _state.update { it.copy(form = transform(it.form)) }
    }

    private fun MutableStateFlow<EntryUiState>.updateAndGetForm(
        transform: (EntryFormState) -> EntryFormState,
    ): EntryFormState {
        updateForm(transform)
        return value.form
    }

    companion object {
        val Factory = sakuViewModelFactory {
            TransactionEntryViewModel(transactions, categories, accounts)
        }
    }
}

/**
 * Carries [Transaction.createdAt] and [Transaction.importBatchId] across an edit.
 * Rebuilding the row from the form alone would silently detach an imported
 * transaction from its batch, and reversing that batch would then leave it behind.
 *
 * The sheet no longer shows subcategories (dropped for search, 30 Oct 2026), but older
 * rows may still carry one. It is kept while the category is unchanged, so fixing a
 * note does not wipe a value the user cannot see, and cleared when the category
 * changes, because a subcategory belongs to exactly one category.
 */
internal fun EntryFormState.toTransaction(
    original: Transaction,
    occurredAt: kotlinx.datetime.Instant,
): Transaction = original.copy(
    occurredAt = occurredAt,
    type = type,
    amount = money,
    accountId = requireNotNull(accountId),
    toAccountId = toAccountId,
    categoryId = categoryId,
    subcategoryId = original.subcategoryId.takeIf { categoryId == original.categoryId },
    note = note.takeIf { it.isNotBlank() },
)
