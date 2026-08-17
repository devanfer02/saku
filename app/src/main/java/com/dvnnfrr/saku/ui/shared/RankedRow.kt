package com.dvnnfrr.saku.ui.shared

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dvnnfrr.saku.domain.ColorSlot
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.ui.theme.SakuPreview
import com.dvnnfrr.saku.ui.theme.SakuPreviewSurface
import com.dvnnfrr.saku.ui.theme.SakuTheme
import com.dvnnfrr.saku.ui.theme.formatIdr

/**
 * A colour chip, a name, an optional share and a value.
 *
 * Doubles as the donut's legend: this is where a segment's colour gets its name, which
 * is what keeps the chart from conveying identity by colour alone.
 */
@Composable
fun RankedRow(
    colorSlot: ColorSlot?,
    name: String,
    value: Money,
    modifier: Modifier = Modifier,
    sharePercent: Int? = null,
    /**
     * A second line under the name. The accounts list needs it for "never reconciled"
     * and "last reconciled <date>", which the spec requires in distinct colours.
     */
    supporting: String? = null,
    supportingColor: Color? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = SakuTheme.colors
    val spacing = SakuTheme.spacing

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .defaultMinSize(minHeight = spacing.touchTarget)
            .padding(horizontal = spacing.screen, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        ColorSwatch(colorSlot)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = SakuTheme.typography.body,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            supporting?.let {
                Text(
                    text = it,
                    style = SakuTheme.typography.caption,
                    color = supportingColor ?: colors.ink2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        sharePercent?.let {
            Text(
                text = "$it%",
                style = SakuTheme.typography.amountSmall,
                color = colors.ink2,
                textAlign = TextAlign.End,
                maxLines = 1,
            )
        }
        Text(
            text = formatIdr(value, withPrefix = false),
            style = SakuTheme.typography.amountMedium,
            color = colors.ink,
            textAlign = TextAlign.End,
            maxLines = 1,
        )
        trailing?.invoke(this)
    }
}

@SakuPreview
@Composable
private fun RankedRowPreview() {
    SakuPreviewSurface(padded = false) {
        Column(modifier = Modifier.width(360.dp)) {
            RankedRow(ColorSlot(0), "Pemberian", Money(8_400_000), sharePercent = 53)
            RankedRow(ColorSlot(1), "Makan & Minum", Money(3_100_000), sharePercent = 22)
            RankedRow(
                colorSlot = ColorSlot(2),
                name = "Cash",
                value = Money(385_000),
                supporting = "Never reconciled",
                supportingColor = SakuTheme.colors.warning,
            )
            RankedRow(null, "Other", Money(1_200_000), sharePercent = 8)
        }
    }
}
