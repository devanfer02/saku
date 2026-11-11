package com.dvnnfrr.saku.ui.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.TxnType
import com.dvnnfrr.saku.ui.theme.MoneySign
import com.dvnnfrr.saku.ui.theme.SakuPreview
import com.dvnnfrr.saku.ui.theme.SakuPreviewSurface
import com.dvnnfrr.saku.ui.theme.SakuTheme
import com.dvnnfrr.saku.ui.theme.forDirection
import com.dvnnfrr.saku.ui.theme.formatMoney

/**
 * An amount, always as `Rp` with full-stop groups, in its direction's colour.
 *
 * Colour is the fast cue but never the only one: [sign] puts the direction in the text
 * itself, and the semantic description names it, so a screen reader hears "Expense
 * minus Rp 196.000" where a sighted user sees the sign.
 */
@Composable
fun MoneyText(
    money: Money,
    modifier: Modifier = Modifier,
    /**
     * Null renders in neutral ink and speaks no direction: a balance, a net worth, a
     * period total. A net has no direction, and announcing "Expense" beside a field
     * already labelled "Out" is noise.
     */
    type: TxnType? = null,
    style: TextStyle = SakuTheme.typography.amountMedium,
    /** Balance by default. A transaction amount passes [MoneySign.FLOW], a net [MoneySign.NET]. */
    sign: MoneySign = MoneySign.BALANCE,
    color: Color? = null,
    spokenType: String? = null,
    contentDescription: String? = null,
    /**
     * Shrink the font until the amount fits the width it is given, rather than clip or
     * wrap. The amount keeps its full-size height, so a long one never changes the height
     * of the row it sits in. For a slot whose width is fixed by its layout, such as a header.
     */
    shrinkToFit: Boolean = false,
) {
    val text = formatMoney(money, sign, type)
    val spoken = contentDescription
        ?: listOfNotNull(spokenType ?: type?.spokenName, text).joinToString(" ")
    val ink = color ?: type?.let { SakuTheme.colors.forDirection(it) } ?: SakuTheme.colors.ink

    if (!shrinkToFit) {
        Text(
            text = text,
            style = style,
            color = ink,
            maxLines = 1,
            modifier = modifier.semantics { this.contentDescription = spoken },
        )
        return
    }

    BoxWithConstraints(modifier = modifier) {
        val measurer = rememberTextMeasurer()
        val maxWidthPx = constraints.maxWidth
        val bounded = constraints.hasBoundedWidth
        // Text merges its style over LocalTextStyle (letter spacing, for one), so measure
        // what it will draw, or the fitted amount still runs a digit past the edge.
        val drawn = LocalTextStyle.current.merge(style)
        val fitted = remember(text, drawn, maxWidthPx, bounded, measurer) {
            if (!bounded) return@remember drawn
            measurer.fitOnOneLine(text, drawn, maxWidthPx.toFloat())
        }
        // The height the amount takes at full size, kept when it shrinks.
        val fullHeightPx = remember(text, drawn, measurer) {
            measurer.measure(text, drawn, softWrap = false, maxLines = 1).size.height
        }
        val fullHeight = with(LocalDensity.current) { fullHeightPx.toDp() }
        Box(modifier = Modifier.heightIn(min = fullHeight), contentAlignment = Alignment.CenterStart) {
            Text(
                text = text,
                style = fitted,
                color = ink,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.semantics { this.contentDescription = spoken },
            )
        }
    }
}

internal val TxnType.spokenName: String
    get() = when (this) {
        TxnType.INCOME -> "Income"
        TxnType.EXPENSE -> "Expense"
        TxnType.TRANSFER -> "Transfer"
        TxnType.ADJUSTMENT -> "Adjustment"
    }

@SakuPreview
@Composable
private fun MoneyTextPreview() {
    SakuPreviewSurface {
        Column(verticalArrangement = Arrangement.spacedBy(SakuTheme.spacing.sm)) {
            MoneyText(
                Money(16_500_000),
                type = TxnType.INCOME,
                sign = MoneySign.FLOW,
                style = SakuTheme.typography.amountLarge,
            )
            MoneyText(Money(196_000), type = TxnType.EXPENSE, sign = MoneySign.FLOW, color = SakuTheme.colors.ink)
            MoneyText(Money(1_000_000), type = TxnType.TRANSFER, sign = MoneySign.FLOW)
            MoneyText(Money(-75_000), type = TxnType.ADJUSTMENT, sign = MoneySign.FLOW)
            MoneyText(Money(-1_038_500), style = SakuTheme.typography.amountLarge)
            MoneyText(Money(2_473_600), sign = MoneySign.NET)
        }
    }
}
