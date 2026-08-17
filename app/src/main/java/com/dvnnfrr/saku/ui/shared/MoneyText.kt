package com.dvnnfrr.saku.ui.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.TxnType
import com.dvnnfrr.saku.ui.theme.SakuPreview
import com.dvnnfrr.saku.ui.theme.SakuPreviewSurface
import com.dvnnfrr.saku.ui.theme.SakuTheme
import com.dvnnfrr.saku.ui.theme.forDirection
import androidx.compose.ui.graphics.Color
import com.dvnnfrr.saku.ui.theme.formatIdr
import com.dvnnfrr.saku.ui.theme.formatIdrSigned

/**
 * An amount in its direction's colour.
 *
 * Colour is the fast cue but never the only one: the semantic description names the
 * direction, so a screen reader hears "Expense Rp 5.061.400" where a sighted user sees
 * red.
 *
 * @param withPrefix false drops the `Rp` for dense rows.
 */
@Composable
fun MoneyText(
    money: Money,
    modifier: Modifier = Modifier,
    /**
     * Null renders in neutral ink and speaks no direction — a balance, a net worth, a
     * period total. Requiring a type is why the proving slice bypassed this component
     * five times and reimplemented its body inline twice: a net has no direction, and
     * announcing "Expense" beside a field already labelled "Expenses" is noise.
     */
    type: TxnType? = null,
    style: TextStyle = SakuTheme.typography.amountMedium,
    withPrefix: Boolean = true,
    /** A figure that can go either way carries its sign; a directional one does not. */
    signed: Boolean = false,
    color: Color? = null,
    spokenType: String? = null,
    contentDescription: String? = null,
) {
    val text = if (signed) formatIdrSigned(money, withPrefix) else formatIdr(money, withPrefix)
    val spoken = contentDescription
        ?: listOfNotNull(spokenType ?: type?.spokenName, text).joinToString(" ")

    Text(
        text = text,
        style = style,
        color = color ?: type?.let { SakuTheme.colors.forDirection(it) } ?: SakuTheme.colors.ink,
        maxLines = 1,
        modifier = modifier.semantics { this.contentDescription = spoken },
    )
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
                Money(4_200_000),
                type = TxnType.INCOME,
                style = SakuTheme.typography.amountLarge,
            )
            MoneyText(Money(5_061_400), type = TxnType.EXPENSE)
            MoneyText(Money(1_000_000), type = TxnType.TRANSFER)
            MoneyText(Money(-75_000), type = TxnType.ADJUSTMENT)
            MoneyText(Money(12_500), type = TxnType.EXPENSE, withPrefix = false)
            // Neutral and signed: a balance and a net, neither of which has a direction.
            MoneyText(Money(15_955_000), style = SakuTheme.typography.amountLarge)
            MoneyText(Money(-5_061_400), signed = true)
        }
    }
}
