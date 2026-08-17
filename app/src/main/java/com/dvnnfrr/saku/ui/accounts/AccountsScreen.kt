package com.dvnnfrr.saku.ui.accounts

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dvnnfrr.saku.R
import com.dvnnfrr.saku.domain.AccountSummary
import com.dvnnfrr.saku.domain.AccountType
import com.dvnnfrr.saku.domain.ColorSlot
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.ui.shared.ConfirmDialog
import com.dvnnfrr.saku.ui.shared.Donut
import com.dvnnfrr.saku.ui.shared.DonutSegment
import com.dvnnfrr.saku.ui.shared.EmptyState
import com.dvnnfrr.saku.ui.shared.MoneyText
import com.dvnnfrr.saku.ui.shared.RankedRow
import com.dvnnfrr.saku.ui.theme.SakuPreview
import com.dvnnfrr.saku.ui.theme.SakuPreviewSurface
import com.dvnnfrr.saku.ui.theme.SakuTheme
import com.dvnnfrr.saku.ui.theme.formatIdr
import com.dvnnfrr.saku.ui.transactions.dayHeaderLabel
import kotlinx.datetime.LocalDate

/**
 * The Accounts destination: what is held, where it sits, and how far each account has
 * drifted from what is really there.
 *
 * The donut and the list beneath it read the same [AccountsUiState.mix], so the chart
 * and its legend cannot disagree, and the header is folded from the same list of
 * accounts as the rows — a net worth that did not equal the sum of the rows under it
 * would be the least forgivable bug in the app.
 */
@Composable
fun AccountsScreen(
    modifier: Modifier = Modifier,
    viewModel: AccountsViewModel = viewModel(factory = AccountsViewModel.Factory),
) {
    val state by viewModel.state.collectAsState()
    var editing by remember { mutableStateOf<AccountFormState?>(null) }
    var reconciling by remember { mutableStateOf<AccountRowUi?>(null) }
    var archiving by remember { mutableStateOf<AccountRowUi?>(null) }

    val detail = state.detail
    if (detail != null) {
        BackHandler(onBack = viewModel::closeDetail)
        AccountDetailScreen(
            state = detail,
            modifier = modifier,
            onBack = viewModel::closeDetail,
            onEdit = { editing = viewModel.editFormFor(detail.account.id) },
            onReconcile = { reconciling = detail.account },
            onArchive = { archiving = detail.account },
            onRestore = { viewModel.setArchived(detail.account.id, false) },
        )
    } else {
        Box(modifier = modifier.fillMaxSize()) {
            AccountsList(
                state = state,
                onSegmentSelect = viewModel::selectSegment,
                onAccountClick = viewModel::openDetail,
            )

            FloatingActionButton(
                onClick = { editing = viewModel.newAccountForm() },
                containerColor = SakuTheme.colors.accent,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(SakuTheme.spacing.lg),
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.accounts_add))
            }
        }
    }

    editing?.let { form ->
        AccountEditorSheet(
            form = form,
            onDismiss = { editing = null },
            onSubmit = { edited ->
                if (edited.isEditing) viewModel.save(edited) else viewModel.create(edited)
                editing = null
            },
        )
    }

    reconciling?.let { account ->
        ReconcileDialog(
            account = account,
            onDismiss = { reconciling = null },
            onConfirm = { trueBalance ->
                viewModel.reconcile(account.id, trueBalance)
                reconciling = null
            },
        )
    }

    archiving?.let { account ->
        ConfirmDialog(
            title = stringResource(R.string.accounts_archive_title, account.name),
            body = stringResource(R.string.accounts_archive_body),
            // No count: an account's transaction count is not on the repository, and a
            // wrong number here would be worse than none. The body says history is kept.
            affectedCount = null,
            confirmLabel = stringResource(R.string.accounts_archive),
            onConfirm = {
                viewModel.setArchived(account.id, true)
                archiving = null
            },
            onDismiss = { archiving = null },
        )
    }

    state.message?.let { message ->
        AccountsMessageDialog(message = message, onDismiss = viewModel::dismissMessage)
    }
}

@Composable
private fun AccountsList(
    state: AccountsUiState,
    onSegmentSelect: (Int?) -> Unit,
    onAccountClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = SakuTheme.spacing

    if (!state.loading && state.isEmpty) {
        EmptyState(
            icon = Icons.Outlined.AccountBalanceWallet,
            message = stringResource(R.string.accounts_empty),
            modifier = modifier,
        )
        return
    }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        item {
            BalanceChart(state = state, onSegmentSelect = onSegmentSelect)
        }
        item {
            SummaryFigures(summary = state.summary)
            HorizontalDivider(color = SakuTheme.colors.line)
        }

        state.groups.forEach { group ->
            item(key = "header-${group.type}") {
                SectionHeading(stringResource(group.type.labelRes()))
            }
            items(group.rows, key = { it.id }) { row ->
                AccountRow(
                    row = row,
                    selected = row.id == state.selectedAccountId,
                    onClick = { onAccountClick(row.id) },
                )
            }
        }

        if (state.archived.isNotEmpty()) {
            item(key = "header-archived") {
                SectionHeading(stringResource(R.string.accounts_archived))
            }
            items(state.archived, key = { "archived-${it.id}" }) { row ->
                AccountRow(row = row, selected = false, onClick = { onAccountClick(row.id) })
            }
        }

        // Clears the floating action button, which would otherwise sit on the last row.
        item { Box(modifier = Modifier.fillMaxWidth().padding(spacing.xxl)) }
    }
}

/**
 * The donut, plus whatever the centre should currently read.
 *
 * With a segment selected the centre names that account instead of the total, which is
 * what makes a tap on the ring mean something. Colour is never the only cue: the same
 * selection lights up the account's row in the list below.
 */
@Composable
private fun BalanceChart(
    state: AccountsUiState,
    onSegmentSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val byId = state.rows.associateBy { it.id }
    val segments = state.mix.mapNotNull { share ->
        val row = byId[share.accountId] ?: return@mapNotNull null
        DonutSegment(slot = row.colorSlot, value = row.balance.rupiah, label = row.name)
    }
    val selectedIndex = state.selectedAccountId
        ?.let { id -> state.mix.indexOfFirst { it.accountId == id } }
        ?.takeIf { it >= 0 }
    val selected = state.selected

    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = SakuTheme.spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (segments.isEmpty()) {
            Text(
                text = stringResource(R.string.accounts_donut_empty),
                style = SakuTheme.typography.caption,
                color = SakuTheme.colors.ink3,
                modifier = Modifier.padding(bottom = SakuTheme.spacing.sm),
            )
        }
        Donut(
            segments = segments,
            centerLabel = selected?.name ?: stringResource(R.string.label_total),
            centerValue = selected?.balance ?: state.summary.total,
            selectedIndex = selectedIndex,
            onSegmentSelect = onSegmentSelect,
        )
    }
}

@Composable
private fun SummaryFigures(summary: AccountSummary, modifier: Modifier = Modifier) {
    val spacing = SakuTheme.spacing
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.screen, vertical = spacing.md),
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Figure(
            label = stringResource(R.string.accounts_spendable),
            value = summary.spendable,
            modifier = Modifier.weight(1f),
        )
        Figure(
            label = stringResource(R.string.accounts_invested),
            value = summary.invested,
            modifier = Modifier.weight(1f),
        )
        // Only once a liability account exists. A Liabilities figure parked at zero for
        // ever reads as a debt of nothing rather than as no debts.
        summary.owed?.let {
            Figure(
                label = stringResource(R.string.accounts_liabilities),
                value = it,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun Figure(label: String, value: Money, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = SakuTheme.typography.label,
            color = SakuTheme.colors.ink2,
            textAlign = TextAlign.Center,
        )
        // type = null: a balance has no direction, and announcing one as an expense is
        // both wrong and the reason MoneyText made the parameter optional.
        MoneyText(
            money = value,
            type = null,
            style = SakuTheme.typography.amountMedium,
            contentDescription = "$label ${formatIdr(value)}",
        )
    }
}

@Composable
private fun SectionHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = SakuTheme.typography.label,
        color = SakuTheme.colors.ink2,
        modifier = modifier.padding(
            start = SakuTheme.spacing.screen,
            end = SakuTheme.spacing.screen,
            top = SakuTheme.spacing.lg,
            bottom = SakuTheme.spacing.xs,
        ),
    )
}

/**
 * One account, as the donut's legend and as the way into its detail.
 *
 * The row is the accessible path to everything the ring offers: it names the account
 * the colour stands for, states its share as a number, and is a full-height tap target,
 * none of which an 18dp arc can claim.
 */
@Composable
private fun AccountRow(
    row: AccountRowUi,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SakuTheme.colors

    RankedRow(
        colorSlot = row.colorSlot,
        name = row.name,
        value = row.balance,
        sharePercent = row.sharePercent,
        supporting = row.reconciliation.supportingText(),
        supportingColor = when (row.reconciliation) {
            is Reconciliation.Never -> colors.expense
            is Reconciliation.Stale -> colors.warning
            is Reconciliation.Current -> colors.ink3
        },
        onClick = onClick,
        // `selected` and nothing else. The row is a merged semantics node, so a
        // contentDescription here would replace the name, the share and the balance
        // with one sentence — the opposite of what a legend row is for.
        modifier = modifier
            .then(if (selected) Modifier.background(colors.accentWash) else Modifier)
            .semantics { this.selected = selected },
    )
}

@Composable
private fun Reconciliation.supportingText(): String = when (this) {
    is Reconciliation.Never -> stringResource(R.string.accounts_never_reconciled)
    is Reconciliation.Stale -> stringResource(R.string.accounts_stale, lastReconciled.label())
    is Reconciliation.Current ->
        stringResource(R.string.accounts_reconciled_on, lastReconciled.label())
}

/**
 * Dates read the same everywhere in the app, so this borrows the transaction list's
 * label rather than growing a second format that could drift from it.
 */
internal fun LocalDate.label(): String = dayHeaderLabel()

@StringRes
internal fun AccountType.labelRes(): Int = when (this) {
    AccountType.CASH -> R.string.accounts_type_cash
    AccountType.BANK -> R.string.accounts_type_bank
    AccountType.INVESTMENT -> R.string.accounts_type_investment
    AccountType.LIABILITY -> R.string.accounts_type_liability
}

@Composable
private fun AccountsMessageDialog(message: AccountsMessage, onDismiss: () -> Unit) {
    when (message) {
        is AccountsMessage.Refused -> ConfirmDialog(
            title = stringResource(R.string.accounts_refused_title),
            // The repository's own words: the opening-date rule names the account whose
            // cutover is too late, and that name is the whole content of the message.
            body = message.reason,
            affectedCount = null,
            confirmLabel = stringResource(R.string.action_done),
            onConfirm = onDismiss,
            onDismiss = onDismiss,
        )

        is AccountsMessage.Reconciled -> ConfirmDialog(
            title = stringResource(R.string.accounts_reconcile),
            body = message.adjustment?.let {
                stringResource(
                    R.string.accounts_reconciled_message,
                    message.accountName,
                    formatIdr(it),
                )
            } ?: stringResource(
                R.string.accounts_reconciled_matched_message,
                message.accountName,
            ),
            affectedCount = null,
            confirmLabel = stringResource(R.string.action_done),
            onConfirm = onDismiss,
            onDismiss = onDismiss,
        )
    }
}

@SakuPreview
@Composable
private fun AccountsScreenPreview() {
    SakuPreviewSurface(padded = false) {
        AccountsList(
            state = previewState(),
            onSegmentSelect = {},
            onAccountClick = {},
        )
    }
}

@SakuPreview
@Composable
private fun AccountsScreenSelectedPreview() {
    SakuPreviewSurface(padded = false) {
        AccountsList(
            state = previewState().copy(selectedAccountId = 2),
            onSegmentSelect = {},
            onAccountClick = {},
        )
    }
}

internal fun previewState(): AccountsUiState {
    val asOf = LocalDate(2026, 8, 17)
    fun row(
        id: Long,
        name: String,
        type: AccountType,
        slot: Int,
        balance: Long,
        reconciliation: Reconciliation,
        share: Int?,
    ) = AccountRowUi(
        id = id,
        name = name,
        type = type,
        colorSlot = ColorSlot(slot),
        balance = Money(balance),
        archived = false,
        reconciliation = reconciliation,
        sharePercent = share,
        openingBalance = Money(balance),
        openingAsOf = asOf,
    )

    val bca = row(
        1, "BCA", AccountType.BANK, 0, 8_420_000,
        Reconciliation.Current(LocalDate(2026, 8, 14)), 53,
    )
    val jago = row(
        2, "Jago", AccountType.BANK, 1, 2_150_000,
        Reconciliation.Stale(LocalDate(2026, 6, 20)), 14,
    )
    val cash = row(3, "Cash", AccountType.CASH, 2, 385_000, Reconciliation.Never, 2)
    val rdpu = row(
        4, "RDPU SucorInvest", AccountType.INVESTMENT, 3, 5_000_000,
        Reconciliation.Never, 31,
    )
    val card = row(
        5, "Kartu Kredit", AccountType.LIABILITY, 4, -2_000_000,
        Reconciliation.Never, null,
    )

    return AccountsUiState(
        loading = false,
        summary = AccountSummary(
            spendable = Money(10_955_000),
            invested = Money(5_000_000),
            owed = Money(2_000_000),
        ),
        groups = listOf(
            AccountGroupUi(AccountType.CASH, listOf(cash)),
            AccountGroupUi(AccountType.BANK, listOf(bca, jago)),
            AccountGroupUi(AccountType.INVESTMENT, listOf(rdpu)),
            AccountGroupUi(AccountType.LIABILITY, listOf(card)),
        ),
        mix = listOf(
            AccountShare(1, 53),
            AccountShare(4, 31),
            AccountShare(2, 14),
            AccountShare(3, 2),
        ),
    )
}
