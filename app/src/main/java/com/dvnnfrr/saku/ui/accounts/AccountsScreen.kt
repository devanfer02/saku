package com.dvnnfrr.saku.ui.accounts

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dvnnfrr.saku.R
import com.dvnnfrr.saku.domain.AccountSummary
import com.dvnnfrr.saku.domain.AccountType
import com.dvnnfrr.saku.domain.ColorSlot
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.ui.shared.ConfirmDialog
import com.dvnnfrr.saku.ui.shared.EmptyState
import com.dvnnfrr.saku.ui.shared.MoneyText
import com.dvnnfrr.saku.ui.shared.RankedRow
import com.dvnnfrr.saku.ui.theme.SakuPreview
import com.dvnnfrr.saku.ui.theme.SakuPreviewSurface
import com.dvnnfrr.saku.ui.theme.SakuTheme
import com.dvnnfrr.saku.ui.theme.formatIdr
import com.dvnnfrr.saku.ui.transactions.dateLabel
import kotlinx.datetime.LocalDate

/**
 * The Accounts destination: what is held, how it splits, and which balances are not
 * yet trustworthy.
 *
 * The header is folded from the same list of accounts as the rows, so a net worth that
 * did not equal the sum of the rows under it cannot happen.
 */
@Composable
fun AccountsScreen(
    modifier: Modifier = Modifier,
    viewModel: AccountsViewModel = viewModel(factory = AccountsViewModel.Factory),
) {
    val state by viewModel.state.collectAsState()
    var editing by remember { mutableStateOf<AccountFormState?>(null) }
    var checking by remember { mutableStateOf<AccountRowUi?>(null) }
    var archiving by remember { mutableStateOf<AccountRowUi?>(null) }

    val detail = state.detail
    if (detail != null) {
        BackHandler(onBack = viewModel::closeDetail)
        AccountDetailScreen(
            state = detail,
            modifier = modifier,
            onBack = viewModel::closeDetail,
            onEdit = { editing = viewModel.editFormFor(detail.account.id) },
            onCheckBalance = { checking = detail.account },
            onArchive = { archiving = detail.account },
            onRestore = { viewModel.setArchived(detail.account.id, false) },
        )
    } else {
        Box(modifier = modifier.fillMaxSize()) {
            AccountsList(
                state = state,
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

    checking?.let { account ->
        ReconcileDialog(
            account = account,
            onDismiss = { checking = null },
            onConfirm = { trueBalance ->
                viewModel.reconcile(account.id, trueBalance)
                checking = null
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
        item(key = "net-worth") {
            NetWorthHeader(split = state.split)
            HorizontalDivider(color = SakuTheme.colors.line)
        }

        state.groups.forEach { group ->
            item(key = "header-${group.type}") {
                SectionHeading(stringResource(group.type.labelRes()))
            }
            items(group.rows, key = { it.id }) { row ->
                AccountRow(row = row, onClick = { onAccountClick(row.id) })
            }
        }

        if (state.archived.isNotEmpty()) {
            item(key = "header-archived") {
                SectionHeading(stringResource(R.string.accounts_archived))
            }
            items(state.archived, key = { "archived-${it.id}" }) { row ->
                AccountRow(row = row, onClick = { onAccountClick(row.id) })
            }
        }

        // Clears the floating action button, which would otherwise sit on the last row.
        item { Box(modifier = Modifier.fillMaxWidth().padding(spacing.xxl)) }
    }
}

/**
 * Net worth as the largest figure, then one bar split into Spendable and Invested with
 * both amounts written out, and what is owed beside them once a liability exists.
 *
 * The bar is ink and secondary ink rather than chart colours: the chart slots are pinned
 * to accounts for life, and a blue Spendable segment would read as "this is BCA".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NetWorthHeader(split: NetWorthSplit, modifier: Modifier = Modifier) {
    val colors = SakuTheme.colors
    val spacing = SakuTheme.spacing
    val netWorthLabel = stringResource(R.string.accounts_net_worth)
    val spendableLabel = stringResource(R.string.accounts_spendable)
    val investedLabel = stringResource(R.string.accounts_invested)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.screen, vertical = spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Text(
            text = netWorthLabel,
            style = SakuTheme.typography.caption,
            color = colors.ink2,
        )
        MoneyText(
            money = split.total,
            style = SakuTheme.typography.amountLarge,
            contentDescription = "$netWorthLabel ${formatIdr(split.total)}",
        )

        if (split.hasBar) {
            SplitBar(
                split = split,
                description = stringResource(
                    R.string.accounts_split_description,
                    spendableLabel,
                    formatIdr(split.spendable),
                    investedLabel,
                    formatIdr(split.invested),
                ),
            )
        } else {
            Text(
                text = stringResource(R.string.accounts_split_empty),
                style = SakuTheme.typography.caption,
                color = colors.ink2,
            )
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            SplitFigure(spendableLabel, split.spendable, colors.ink)
            SplitFigure(investedLabel, split.invested, colors.ink2)
            // Only once a liability account exists. A figure parked at zero for ever
            // reads as a debt of nothing rather than as no debts.
            split.owed?.let { SplitFigure(stringResource(R.string.accounts_liabilities), it, dot = null) }
        }
    }
}

@Composable
private fun SplitBar(split: NetWorthSplit, description: String) {
    val colors = SakuTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(SPLIT_BAR_HEIGHT)
            .clip(RoundedCornerShape(SPLIT_BAR_HEIGHT / 2))
            .background(colors.surfaceAlt)
            // The figures beneath already speak; the bar adds one sentence, not two loose
            // coloured boxes.
            .clearAndSetSemantics { contentDescription = description },
    ) {
        // Weights are the permille shares; only the layout turns them into a fraction.
        if (split.spendablePermille > 0) {
            Box(Modifier.weight(split.spendablePermille.toFloat()).fillMaxHeight().background(colors.ink))
        }
        if (split.investedPermille > 0) {
            Box(Modifier.weight(split.investedPermille.toFloat()).fillMaxHeight().background(colors.ink2))
        }
    }
}

@Composable
private fun SplitFigure(label: String, value: Money, dot: Color?) {
    val spacing = SakuTheme.spacing
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        dot?.let { Box(Modifier.size(spacing.swatch).background(it, CircleShape)) }
        Text(text = label, style = SakuTheme.typography.caption, color = SakuTheme.colors.ink2)
        MoneyText(
            money = value,
            style = SakuTheme.typography.amountSmall,
            contentDescription = "$label ${formatIdr(value)}",
        )
    }
}

private val SPLIT_BAR_HEIGHT = 10.dp

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

/** One account, and the way into its detail. */
@Composable
private fun AccountRow(
    row: AccountRowUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RankedRow(
        colorSlot = row.colorSlot,
        name = row.name,
        value = row.balance,
        supporting = row.statusText(),
        supportingColor = row.statusColor(),
        valueColor = SakuTheme.colors.warning.takeIf { row.belowZero },
        onClick = onClick,
        modifier = modifier,
    )
}

/**
 * The one line under an account's name, shared by the list and the detail so the two
 * never word a state differently.
 *
 * Amber is spent only where the figure needs attention: a balance below zero, or a check
 * past the staleness window. An account that was never checked is the normal state of
 * every imported account and reads in plain secondary ink.
 */
@Composable
internal fun AccountRowUi.statusText(): String = when {
    belowZero -> stringResource(R.string.accounts_below_zero)
    else -> when (val check = reconciliation) {
        is Reconciliation.Never -> stringResource(R.string.accounts_not_checked)
        is Reconciliation.Stale -> stringResource(R.string.accounts_checked_on, check.lastReconciled.label())
        is Reconciliation.Current -> stringResource(R.string.accounts_checked_on, check.lastReconciled.label())
    }
}

@Composable
internal fun AccountRowUi.statusColor(): Color = when {
    belowZero || reconciliation is Reconciliation.Stale -> SakuTheme.colors.warning
    else -> SakuTheme.colors.ink2
}

/** Dates read the same everywhere in the app, so this borrows the shared label. */
internal fun LocalDate.label(): String = dateLabel()

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
        AccountsList(state = previewState(), onAccountClick = {})
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
    ) = AccountRowUi(
        id = id,
        name = name,
        type = type,
        colorSlot = ColorSlot(slot),
        balance = Money(balance),
        archived = false,
        reconciliation = reconciliation,
        belowZero = balance < 0 && type != AccountType.LIABILITY,
        openingBalance = Money(balance),
        openingAsOf = asOf,
    )

    val bca = row(1, "BCA", AccountType.BANK, 0, 2_745_315, Reconciliation.Current(LocalDate(2026, 10, 14)))
    val jago = row(2, "Jago", AccountType.BANK, 1, 1_300_000, Reconciliation.Stale(LocalDate(2026, 6, 20)))
    val cash = row(3, "Cash", AccountType.CASH, 2, -1_038_500, Reconciliation.Never)
    val rdpu = row(4, "RDPU SucorInvest", AccountType.INVESTMENT, 3, 14_550_800, Reconciliation.Never)
    val card = row(5, "Kartu Kredit", AccountType.LIABILITY, 4, -2_000_000, Reconciliation.Never)

    return AccountsUiState(
        loading = false,
        summary = AccountSummary(
            spendable = Money(3_006_815),
            invested = Money(14_550_800),
            owed = Money(2_000_000),
        ),
        groups = listOf(
            AccountGroupUi(AccountType.CASH, listOf(cash)),
            AccountGroupUi(AccountType.BANK, listOf(bca, jago)),
            AccountGroupUi(AccountType.INVESTMENT, listOf(rdpu)),
            AccountGroupUi(AccountType.LIABILITY, listOf(card)),
        ),
    )
}
