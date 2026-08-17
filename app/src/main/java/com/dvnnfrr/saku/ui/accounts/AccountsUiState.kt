package com.dvnnfrr.saku.ui.accounts

import com.dvnnfrr.saku.domain.AccountSummary
import com.dvnnfrr.saku.domain.AccountType
import com.dvnnfrr.saku.domain.ColorSlot
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.TxnType
import kotlinx.datetime.LocalDate

/** Everything the accounts list renders. */
data class AccountsUiState(
    val loading: Boolean = true,
    val summary: AccountSummary = AccountSummary(Money.ZERO, Money.ZERO, owed = null),
    val groups: List<AccountGroupUi> = emptyList(),
    /** Donut segments, largest first. Only accounts holding a positive balance. */
    val mix: List<AccountShare> = emptyList(),
    /** Listed apart, so an archived account can be brought back. */
    val archived: List<AccountRowUi> = emptyList(),
    /**
     * The account a donut tap has picked out, if any. Selection only changes what the
     * centre reads and which row is emphasised; it moves no money and survives nothing.
     */
    val selectedAccountId: Long? = null,
    val detail: AccountDetailUiState? = null,
    val message: AccountsMessage? = null,
) {
    val rows: List<AccountRowUi> get() = groups.flatMap { it.rows }

    val isEmpty: Boolean get() = groups.isEmpty() && archived.isEmpty()

    val selected: AccountRowUi?
        get() = selectedAccountId?.let { id -> rows.firstOrNull { it.id == id } }

    /** The mix as segment indices, so a donut tap can be mapped back to an account. */
    fun accountIdForSegment(index: Int): Long? = mix.getOrNull(index)?.accountId
}

/** The account detail: what it holds, and every row that got it there. */
data class AccountDetailUiState(
    val account: AccountRowUi,
    /** Newest first. */
    val entries: List<LedgerEntryUi> = emptyList(),
)

/** One ledger row: what it was, what it did here, and where it left the balance. */
data class LedgerEntryUi(
    val transactionId: Long,
    val date: LocalDate,
    val label: String?,
    val note: String?,
    val type: TxnType,
    val effect: Money,
    val runningBalance: Money,
)

/**
 * Something the screen has to say after an action.
 *
 * [Refused] carries the repository's own message rather than a generic one: the
 * opening-date rule names the account whose cutover is too late, and that name is the
 * whole content of the message.
 */
sealed interface AccountsMessage {
    data class Refused(val reason: String) : AccountsMessage

    /** Null [adjustment] means the balance already matched and nothing was written. */
    data class Reconciled(val accountName: String, val adjustment: Money?) : AccountsMessage
}

/** The form behind create and edit. Both write the same four fields. */
data class AccountFormState(
    /** Null while creating. */
    val id: Long? = null,
    val name: String = "",
    val type: AccountType = AccountType.BANK,
    val colorSlot: ColorSlot? = null,
    val openingBalance: Money = Money.ZERO,
    val openingAsOf: LocalDate,
) {
    val isEditing: Boolean get() = id != null

    val nameIsBlank: Boolean get() = name.isBlank()
}
