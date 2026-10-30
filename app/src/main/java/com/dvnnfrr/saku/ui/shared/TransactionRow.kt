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
import com.dvnnfrr.saku.ui.theme.MoneySign
import com.dvnnfrr.saku.ui.theme.SakuPreview
import com.dvnnfrr.saku.ui.theme.SakuPreviewSurface
import com.dvnnfrr.saku.ui.theme.SakuTheme
import com.dvnnfrr.saku.ui.theme.forSlotOrNeutral

private const val ARROW = "→"
private const val SEPARATOR = " · "

/** What a [TransactionRow] renders, resolved from the transaction's shape. */
@Immutable
data class TransactionRowLabels(
    val primary: String,
    /** `Account · Subcategory · note`, never empty: every row names its account. */
    val secondary: String,
    val spokenSecondary: String,
    val showsBothAccounts: Boolean,
)

/**
 * Works out a row's two lines.
 *
 * The second line leads with the account, then the subcategory, then the note. That
 * order is deliberate: the line ellipsises from the end, so a long note is what gets
 * cut, never the account (gap 2, a wrong account is how a bad import gets noticed) or
 * the subcategory (the field the old app never captured).
 *
 * A transfer names both accounts with an arrow between them, because "which account"
 * is the whole content of a transfer. Pure, and every string it can produce arrives as a
 * parameter, so the component carries no copy of its own.
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
    val sub = subcategory?.takeIf { it.isNotBlank() }
    val text = note?.takeIf { it.isNotBlank() }

    val accounts = if (showsBoth) "$accountName $ARROW $toAccountName" else accountName
    val spokenAccounts =
        if (showsBoth) "$accountName $conjunction $toAccountName" else accountName

    return TransactionRowLabels(
        primary = primary,
        secondary = listOfNotNull(accounts, sub, text).joinToString(SEPARATOR),
        // Commas, so TalkBack pauses where the eye sees a dot and never reads the arrow.
        spokenSecondary = listOfNotNull(spokenAccounts, sub, text).joinToString(", "),
        showsBothAccounts = showsBoth,
    )
}

/**
 * One transaction in a list. Consumed by the Daily list and by stats drill-through, so
 * both show the same thing.
 *
 * The amount carries its direction as a sign. Income keeps the income colour because it
 * is rare and worth spotting; an expense renders in ink so that the expense colour keeps
 * meaning "look at this" on the totals. A transfer reads unsigned in the neutral ink:
 * money that moved between the owner's own accounts left nothing.
 */
@Composable
fun TransactionRow(
    amount: Money,
    type: TxnType,
    accountName: String,
    modifier: Modifier = Modifier,
    categorySlot: ColorSlot? = null,
    category: String? = null,
    subcategory: String? = null,
    note: String? = null,
    toAccountName: String? = null,
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
        ColorSwatch(categorySlot)
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
            Text(
                text = labels.secondary,
                style = SakuTheme.typography.caption,
                color = colors.ink2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { contentDescription = labels.spokenSecondary },
            )
        }

        MoneyText(
            money = amount,
            type = type,
            sign = MoneySign.FLOW,
            color = if (type == TxnType.EXPENSE) colors.ink else null,
            style = SakuTheme.typography.amountMedium,
        )
    }
}

/**
 * The last-resort title when a row has no category: a transfer never has one, and an
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
                amount = Money(30_000),
                type = TxnType.EXPENSE,
                accountName = "BCA",
                categorySlot = ColorSlot(3),
                category = "Jajan & Hiburan",
                note = "Spotify Premium",
            )
            TransactionRow(
                amount = Money(45_000),
                type = TxnType.EXPENSE,
                accountName = "BCA",
                categorySlot = ColorSlot(1),
                category = "Makan & Minum",
                subcategory = "Warung",
                note = "Nasi padang",
            )
            TransactionRow(
                amount = Money(16_500_000),
                type = TxnType.INCOME,
                accountName = "BCA",
                categorySlot = ColorSlot(0),
                category = "Kerjaan",
            )
            TransactionRow(
                amount = Money(1_000_000),
                type = TxnType.TRANSFER,
                accountName = "BCA",
                toAccountName = "Bibit",
            )
            TransactionRow(
                amount = Money(-25_000),
                type = TxnType.ADJUSTMENT,
                accountName = "Tunai",
                note = "Balance checked",
            )
        }
    }
}
