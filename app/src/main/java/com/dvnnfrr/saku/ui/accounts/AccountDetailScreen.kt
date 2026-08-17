package com.dvnnfrr.saku.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.dvnnfrr.saku.R
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.TxnType
import com.dvnnfrr.saku.ui.shared.ColorSwatch
import com.dvnnfrr.saku.ui.shared.EmptyState
import com.dvnnfrr.saku.ui.shared.MoneyText
import com.dvnnfrr.saku.ui.theme.SakuPreview
import com.dvnnfrr.saku.ui.theme.SakuPreviewSurface
import com.dvnnfrr.saku.ui.theme.SakuTheme
import com.dvnnfrr.saku.ui.theme.formatIdr
import kotlinx.datetime.LocalDate

/**
 * One account: what it holds, and every row that got it there.
 *
 * The ledger is the answer to "why is this figure what it is". Each row states what it
 * did to this account — signed, from this account's point of view, so the receiving
 * side of a transfer reads as money arriving — and what the balance was afterwards. The
 * last running figure is the balance in the header, which is the check that the fold
 * and the aggregate still agree.
 */
@Composable
fun AccountDetailScreen(
    state: AccountDetailUiState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onReconcile: () -> Unit,
    onArchive: () -> Unit,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = SakuTheme.spacing
    val account = state.account

    Column(modifier = modifier.fillMaxSize()) {
        DetailTopBar(
            account = account,
            onBack = onBack,
            onEdit = onEdit,
            onArchive = onArchive,
            onRestore = onRestore,
        )

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.screen),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            // type = null: a balance has no direction. Announcing it as an expense
            // because it happens to be negative would misdescribe an overdrawn card.
            MoneyText(
                money = account.balance,
                type = null,
                style = SakuTheme.typography.display,
                contentDescription = formatIdr(account.balance),
            )
            Text(
                text = stringResource(
                    R.string.accounts_detail_opening,
                    formatIdr(account.openingBalance),
                    account.openingAsOf.label(),
                ),
                style = SakuTheme.typography.caption,
                color = SakuTheme.colors.ink2,
            )
            Text(
                text = account.reconciliation.supportingLabel(),
                style = SakuTheme.typography.caption,
                color = when (account.reconciliation) {
                    is Reconciliation.Never -> SakuTheme.colors.expense
                    is Reconciliation.Stale -> SakuTheme.colors.warning
                    is Reconciliation.Current -> SakuTheme.colors.ink3
                },
            )
            TextButton(onClick = onReconcile, modifier = Modifier.padding(top = spacing.xs)) {
                Text(
                    text = stringResource(R.string.accounts_reconcile),
                    color = SakuTheme.colors.accent,
                    style = SakuTheme.typography.bodyStrong,
                )
            }
        }

        HorizontalDivider(color = SakuTheme.colors.line)

        if (state.entries.isEmpty()) {
            EmptyState(
                icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                message = stringResource(R.string.accounts_detail_empty),
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.entries, key = { it.transactionId }) { entry ->
                    LedgerRow(entry)
                }
            }
        }
    }
}

@Composable
private fun DetailTopBar(
    account: AccountRowUi,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onRestore: () -> Unit,
) {
    val spacing = SakuTheme.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.sm, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.accounts_back),
                tint = SakuTheme.colors.ink,
            )
        }
        ColorSwatch(account.colorSlot)
        Text(
            text = account.name,
            style = SakuTheme.typography.title,
            color = SakuTheme.colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onEdit) {
            Icon(
                Icons.Filled.Edit,
                contentDescription = stringResource(R.string.accounts_edit),
                tint = SakuTheme.colors.ink2,
            )
        }
        // Archive, never delete. An account named on a transaction keeps its history
        // whatever happens to it here.
        if (account.archived) {
            IconButton(onClick = onRestore) {
                Icon(
                    Icons.Filled.Unarchive,
                    contentDescription = stringResource(R.string.accounts_restore),
                    tint = SakuTheme.colors.ink2,
                )
            }
        } else {
            IconButton(onClick = onArchive) {
                Icon(
                    Icons.Filled.Archive,
                    contentDescription = stringResource(R.string.accounts_archive),
                    tint = SakuTheme.colors.ink2,
                )
            }
        }
    }
}

@Composable
private fun LedgerRow(entry: LedgerEntryUi, modifier: Modifier = Modifier) {
    val spacing = SakuTheme.spacing
    val colors = SakuTheme.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.screen, vertical = spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.label ?: entry.type.fallbackLabel(),
                style = SakuTheme.typography.bodyStrong,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = entry.note ?: entry.date.label(),
                style = SakuTheme.typography.caption,
                color = colors.ink3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            // Signed, because the point of this column is the direction of the effect
            // on THIS account: the same transfer row is negative on one side of it and
            // positive on the other.
            MoneyText(
                money = entry.effect,
                type = entry.type,
                signed = true,
                style = SakuTheme.typography.amountMedium,
            )
            MoneyText(
                money = entry.runningBalance,
                type = null,
                style = SakuTheme.typography.caption,
                withPrefix = false,
                color = colors.ink3,
                contentDescription = stringResource(
                    R.string.accounts_detail_running,
                    formatIdr(entry.runningBalance),
                ),
            )
        }
    }
}

@Composable
private fun Reconciliation.supportingLabel(): String = when (this) {
    is Reconciliation.Never -> stringResource(R.string.accounts_never_reconciled)
    is Reconciliation.Stale -> stringResource(R.string.accounts_stale, lastReconciled.label())
    is Reconciliation.Current ->
        stringResource(R.string.accounts_reconciled_on, lastReconciled.label())
}

/** A ledger row with no category still needs a name; its type is the honest one. */
@Composable
private fun TxnType.fallbackLabel(): String = when (this) {
    TxnType.EXPENSE -> stringResource(R.string.entry_type_expense)
    TxnType.INCOME -> stringResource(R.string.entry_type_income)
    TxnType.TRANSFER -> stringResource(R.string.entry_type_transfer)
    TxnType.ADJUSTMENT -> stringResource(R.string.accounts_reconcile)
}

@SakuPreview
@Composable
private fun AccountDetailPreview() {
    SakuPreviewSurface(padded = false) {
        AccountDetailScreen(
            state = AccountDetailUiState(
                account = previewState().groups[1].rows[0],
                entries = listOf(
                    LedgerEntryUi(
                        transactionId = 3,
                        date = LocalDate(2026, 8, 17),
                        label = null,
                        note = "Reconciled BCA",
                        type = TxnType.ADJUSTMENT,
                        effect = Money(-53_500),
                        runningBalance = Money(8_420_000),
                    ),
                    LedgerEntryUi(
                        transactionId = 2,
                        date = LocalDate(2026, 8, 16),
                        label = null,
                        note = null,
                        type = TxnType.TRANSFER,
                        effect = Money(-500_000),
                        runningBalance = Money(8_473_500),
                    ),
                    LedgerEntryUi(
                        transactionId = 1,
                        date = LocalDate(2026, 8, 15),
                        label = "Makan & Minum",
                        note = "Nasi padang",
                        type = TxnType.EXPENSE,
                        effect = Money(-187_000),
                        runningBalance = Money(8_973_500),
                    ),
                ),
            ),
            onBack = {},
            onEdit = {},
            onReconcile = {},
            onArchive = {},
            onRestore = {},
        )
    }
}
