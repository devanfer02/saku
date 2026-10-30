package com.dvnnfrr.saku.ui.firstrun

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dvnnfrr.saku.data.FirstRunStore
import com.dvnnfrr.saku.data.repository.AccountRepository
import com.dvnnfrr.saku.data.repository.CategoryRepository
import com.dvnnfrr.saku.domain.AccountType
import com.dvnnfrr.saku.domain.CategoryKind
import com.dvnnfrr.saku.sakuViewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone

/**
 * The manual path of first-run setup: an accounts step, then a categories step, and one
 * Done that writes both.
 *
 * Holds only what the user has chosen: the open step, the rows as typed, the ticks, and
 * whether a save is running or was refused. Which rows exist before any edit, what is
 * valid and what Done writes all come from `FirstRunAssembly`.
 *
 * Writes go through the calls the accounts and category screens already use; there is no
 * setup-only write path.
 */
class FirstRunSetupViewModel(
    private val accounts: AccountRepository,
    private val categories: CategoryRepository,
    private val store: FirstRunStore,
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
    private val clock: Clock = Clock.System,
) : ViewModel() {

    private data class Choices(
        val step: SetupStep = SetupStep.ACCOUNTS,
        /** Null until the user edits a row; until then the rows follow the database. */
        val drafts: List<AccountDraft>? = null,
        val showProblems: Boolean = false,
        val picks: CategoryPicks = CategoryPicks(),
        val nextAddedSerial: Int = 0,
        val saving: Boolean = false,
        val refusal: String? = null,
        val finished: Boolean = false,
    )

    private val choices = MutableStateFlow(Choices())

    /**
     * Eager, not `WhileSubscribed`: every edit is applied to the rows on screen, so the
     * state must be current even in the instant before the first frame collects it.
     */
    val state: StateFlow<FirstRunSetupUiState> =
        combine(choices, accounts.observeAll(), categories.observeAll()) { chosen, all, cats ->
            assembleSetup(
                step = chosen.step,
                drafts = chosen.drafts ?: accountDraftsFrom(all),
                showProblems = chosen.showProblems,
                accounts = all,
                picks = chosen.picks,
                categories = cats,
                today = today(clock.now(), zone),
                saving = chosen.saving,
                refusal = chosen.refusal,
                finished = chosen.finished,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = FirstRunSetupUiState(today = today(clock.now(), zone)),
        )

    /**
     * The rows as the user last left them. Before the first edit they are whatever the
     * database lists; from the first edit on they live in [choices], updated in place, so
     * two keystrokes landing before the next frame cannot overwrite each other.
     */
    private fun Choices.rows(): List<AccountDraft> = drafts ?: state.value.accounts.map { it.draft }

    private fun currentDrafts(): List<AccountDraft> = choices.value.rows()

    private fun editDrafts(change: (List<AccountDraft>) -> List<AccountDraft>) =
        choices.update { it.copy(drafts = change(it.rows())) }

    private fun editRow(key: String, change: (AccountDraft) -> AccountDraft) =
        editDrafts { rows -> rows.map { if (it.key == key) change(it) else it } }

    fun rename(key: String, name: String) = editRow(key) { it.copy(name = name) }

    fun retype(key: String, type: AccountType) = editRow(key) { it.copy(type = type) }

    fun setAmount(key: String, raw: String) = editRow(key) { it.copy(amountDigits = amountDigitsFrom(raw)) }

    /** A blank Bank row; Bank because a second bank or e-wallet is the common addition. */
    fun addAccount() {
        choices.update {
            val serial = it.nextAddedSerial
            it.copy(
                drafts = it.rows() + AccountDraft(addedKey(serial), null, "", AccountType.BANK),
                nextAddedSerial = serial + 1,
            )
        }
    }

    /** Only a row added here can go; seeded accounts are archived on the accounts screen. */
    fun removeAccount(key: String) = editDrafts { rows -> rows.filterNot { it.key == key && it.isAdded } }

    /** To the categories step, unless a row has a problem, which then shows. */
    fun next() {
        val drafts = currentDrafts()
        choices.update { it.copy(showProblems = true) }
        viewModelScope.launch {
            if (problemsIn(drafts).isEmpty()) choices.update { it.copy(step = SetupStep.CATEGORIES) }
        }
    }

    private suspend fun problemsIn(drafts: List<AccountDraft>): Map<String, DraftProblem> =
        validateAccountDrafts(drafts, archivedNames(accounts.all()))

    /** True when the step changed; false means the host should leave setup. */
    fun back(): Boolean {
        if (choices.value.step == SetupStep.ACCOUNTS) return false
        choices.update { it.copy(step = SetupStep.ACCOUNTS) }
        return true
    }

    fun toggleCategory(choice: CategoryChoice) = choices.update { it.copy(picks = it.picks.toggle(choice)) }

    fun addCategory(name: String, kind: CategoryKind) =
        choices.update { it.copy(picks = it.picks.add(name, kind)) }

    fun dismissRefusal() = choices.update { it.copy(refusal = null) }

    /**
     * Writes every account, then every chosen category, then records first run as done.
     *
     * Accounts first, so a refusal there leaves no half-chosen categories behind. Added
     * accounts and categories go through `findOrCreate`, so pressing Done again after a
     * failure part way never creates a second one.
     */
    fun done() {
        if (choices.value.saving || choices.value.finished) return
        val drafts = currentDrafts()
        val picks = choices.value.picks
        choices.update { it.copy(saving = true, refusal = null) }

        viewModelScope.launch {
            if (problemsIn(drafts).isNotEmpty()) {
                choices.update { it.copy(saving = false, showProblems = true, step = SetupStep.ACCOUNTS) }
                return@launch
            }
            try {
                val current = accounts.all()
                val asOf = startOfToday(clock.now(), zone)
                planAccountWrites(drafts, current).forEach { write ->
                    when (write) {
                        is AccountWrite.Update -> {
                            write.rename?.let { accounts.rename(write.accountId, it) }
                            write.retype?.let { accounts.retype(write.accountId, it) }
                            accounts.setOpening(write.accountId, write.opening, asOf)
                        }
                        is AccountWrite.Add -> {
                            val id = accounts.findOrCreate(write.name, write.type, asOf)
                            accounts.setOpening(id, write.opening, asOf)
                        }
                    }
                }

                planCategoryWrites(picks, categories.allCategories()).forEach {
                    categories.findOrCreate(it.name, it.kind)
                }

                store.markComplete()
                choices.update { it.copy(saving = false, finished = true) }
            } catch (refusal: IllegalArgumentException) {
                refuse(refusal)
            } catch (refusal: IllegalStateException) {
                refuse(refusal)
            }
        }
    }

    private fun refuse(refusal: RuntimeException) {
        choices.update {
            it.copy(saving = false, showProblems = true, step = SetupStep.ACCOUNTS, refusal = refusal.message.orEmpty())
        }
    }

    companion object {
        val Factory = sakuViewModelFactory { FirstRunSetupViewModel(accounts, categories, firstRun) }
    }
}
