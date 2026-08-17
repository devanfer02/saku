package com.dvnnfrr.saku.ui.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dvnnfrr.saku.domain.ColorSlot
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.TxnType
import com.dvnnfrr.saku.ui.theme.SakuPreview
import com.dvnnfrr.saku.ui.theme.SakuPreviewSurface
import com.dvnnfrr.saku.ui.theme.SakuTheme
import com.dvnnfrr.saku.ui.theme.forSlotOrNeutral

private const val ARROW = "→"

/** What a [TransactionRow] renders, resolved from the transaction's shape. */
@Immutable
data class TransactionRowLabels(
    val primary: String,
    val secondary: String?,
    /** Rendered on the account line, per the spec, not folded in beside the note. */
    val subcategory: String?,
    val accountLine: String,
    val spokenAccountLine: String,
    val showsBothAccounts: Boolean,
)

/**
 * Works out a row's three lines.
 *
 * A transfer names both accounts with an arrow between them, because "which account"
 * is the whole content of a transfer — there is no category to show. Income and expense
 * lead with the category and name the one account they touched.
 *
 * Pure, so the transfer variant is testable without a Compose harness. Every string it
 * can produce arrives as a parameter, so the component carries no copy of its own.
 */
fun transactionRowLabels(
    type: TxnType,
    category: String?,
    subcategory: String?,
    note: String?,
    accountName: String,
    toAccountName: String?,
    titleFallback: String,
    conjunction: String,
): TransactionRowLabels {
    val showsBoth = type == TxnType.TRANSFER && !toAccountName.isNullOrBlank()
    val primary = category?.takeIf { it.isNotBlank() } ?: titleFallback
    // Note only. The spec puts the subcategory on the account line — "an account line
    // showing BCA's colour swatch, 'BCA' and 'Restoran'" — and it matters here beyond
    // layout: subcategory was the field the old app never captured, and folding it in
    // beside the note meant a long note ellipsised it away entirely.
    val secondary = note?.takeIf { it.isNotBlank() }
    val sub = subcategory?.takeIf { it.isNotBlank() }

    val accounts = if (showsBoth) "$accountName $ARROW $toAccountName" else accountName
    val spokenAccounts =
        if (showsBoth) "$accountName $conjunction $toAccountName" else accountName

    return TransactionRowLabels(
        primary = primary,
        secondary = secondary,
        subcategory = sub,
        accountLine = if (sub == null) accounts else "$accounts · $sub",
        spokenAccountLine = if (sub == null) spokenAccounts else "$spokenAccounts, $sub",
        showsBothAccounts = showsBoth,
    )
}

/**
 * One transaction in a list. Consumed by the Daily list and by stats drill-through, so
 * both show the same thing.
 *
 * A transfer renders in the neutral transfer ink rather than as an expense — money that
 * moved between the owner's own accounts left nothing.
 */
@Composable
fun TransactionRow(
    amount: Money,
    type: TxnType,
    accountName: String,
    accountSlot: ColorSlot,
    modifier: Modifier = Modifier,
    category: String? = null,
    subcategory: String? = null,
    note: String? = null,
    toAccountName: String? = null,
    toAccountSlot: ColorSlot? = null,
    titleFallback: String = type.defaultTitle,
    accountConjunction: String = "to",
    onClick: (() -> Unit)? = null,
) {
    val colors = SakuTheme.colors
    val spacing = SakuTheme.spacing
    val labels = transactionRowLabels(
        type = type,
        category = category,
        subcategory = subcategory,
        note = note,
        accountName = accountName,
        toAccountName = toAccountName,
        titleFallback = titleFallback,
        conjunction = accountConjunction,
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .defaultMinSize(minHeight = spacing.rowMinHeight)
            .padding(horizontal = spacing.screen, vertical = spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Text(
                text = labels.primary,
                style = SakuTheme.typography.bodyStrong,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            labels.secondary?.let {
                Text(
                    text = it,
                    style = SakuTheme.typography.caption,
                    color = colors.ink2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                // Otherwise TalkBack announces the arrow glyph between two account
                // names and the direction of a transfer is conveyed visually only.
                modifier = Modifier.semantics(mergeDescendants = true) {
                    contentDescription = labels.spokenAccountLine
                },
            ) {
                ColorSwatch(accountSlot)
                if (labels.showsBothAccounts) {
                    Text(
                        text = accountName,
                        style = SakuTheme.typography.caption,
                        color = colors.ink3,
                        maxLines = 1,
                    )
                    Text(
                        text = ARROW,
                        style = SakuTheme.typography.caption,
                        color = colors.ink3,
                    )
                    toAccountSlot?.let { ColorSwatch(it) }
                    Text(
                        text = toAccountName.orEmpty(),
                        style = SakuTheme.typography.caption,
                        color = colors.ink3,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    labels.subcategory?.let {
                        Text(
                            text = "· $it",
                            style = SakuTheme.typography.caption,
                            color = colors.ink3,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else {
                    Text(
                        text = labels.accountLine,
                        style = SakuTheme.typography.caption,
                        color = colors.ink3,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        MoneyText(
            money = amount,
            type = type,
            style = SakuTheme.typography.amountMedium,
        )
    }
}

/**
 * The last-resort title when a row has no category — a transfer never has one, and an
 * imported row may not either. Overridable through `titleFallback`.
 */
val TxnType.defaultTitle: String
    get() = when (this) {
        TxnType.EXPENSE, TxnType.INCOME -> "Uncategorised"
        TxnType.TRANSFER -> "Transfer"
        TxnType.ADJUSTMENT -> "Adjustment"
    }

@Composable
fun ColorSwatch(slot: ColorSlot?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(SakuTheme.spacing.swatch)
            .background(SakuTheme.colors.forSlotOrNeutral(slot), CircleShape),
    )
}

@SakuPreview
@Composable
private fun TransactionRowPreview() {
    SakuPreviewSurface(padded = false) {
        Column(modifier = Modifier.width(360.dp)) {
            TransactionRow(
                amount = Money(45_000),
                type = TxnType.EXPENSE,
                accountName = "BCA",
                accountSlot = ColorSlot(0),
                category = "Makan & Minum",
                subcategory = "Warung",
                note = "Nasi padang",
            )
            TransactionRow(
                amount = Money(4_200_000),
                type = TxnType.INCOME,
                accountName = "BCA",
                accountSlot = ColorSlot(0),
                category = "Kerjaan",
            )
            TransactionRow(
                amount = Money(1_000_000),
                type = TxnType.TRANSFER,
                accountName = "BCA",
                accountSlot = ColorSlot(0),
                toAccountName = "Bibit",
                toAccountSlot = ColorSlot(2),
            )
            TransactionRow(
                amount = Money(-25_000),
                type = TxnType.ADJUSTMENT,
                accountName = "Tunai",
                accountSlot = ColorSlot(4),
                note = "Reconciled",
            )
        }
    }
}
