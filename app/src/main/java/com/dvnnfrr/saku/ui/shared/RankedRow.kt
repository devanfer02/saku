package com.dvnnfrr.saku.ui.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dvnnfrr.saku.domain.ColorSlot
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.PERMILLE_WHOLE
import com.dvnnfrr.saku.ui.theme.SakuPreview
import com.dvnnfrr.saku.ui.theme.SakuPreviewSurface
import com.dvnnfrr.saku.ui.theme.SakuTheme
import com.dvnnfrr.saku.ui.theme.forSlotOrNeutral

/**
 * A colour chip, a name, an optional share and a value, with an optional bar beneath.
 *
 * The bar is drawn against the largest row on the same scale, never against the total,
 * so the top row fills the width and the rest read as lengths beside it. Angles on a
 * donut fail at exactly this job once most slices are under a tenth.
 */
@Composable
fun RankedRow(
    colorSlot: ColorSlot?,
    name: String,
    value: Money,
    modifier: Modifier = Modifier,
    sharePercent: Int? = null,
    /** Thousandths of the largest row's value, from `barPermille`. Null draws no bar. */
    barPermille: Int? = null,
    /**
     * A second line under the name. The accounts list needs it for the balance check
     * status, which the spec puts in distinct colours.
     */
    supporting: String? = null,
    supportingColor: Color? = null,
    valueColor: Color? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = SakuTheme.colors
    val spacing = SakuTheme.spacing

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .defaultMinSize(minHeight = spacing.touchTarget)
            .padding(horizontal = spacing.screen, vertical = spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Row(
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
                        // Two lines: at a large font scale the warning's instruction was
                        // the part an ellipsis cut ("Set an opening ba…").
                        maxLines = 2,
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
            MoneyText(
                money = value,
                style = SakuTheme.typography.amountMedium,
                color = valueColor,
            )
            trailing?.invoke(this)
        }
        barPermille?.let {
            ShareBar(
                permille = it,
                color = colors.forSlotOrNeutral(colorSlot),
                // Under the name, past the swatch, so the bars start on one edge.
                modifier = Modifier.padding(start = spacing.swatch + spacing.md),
            )
        }
    }
}

@Composable
private fun ShareBar(permille: Int, color: Color, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(BAR_HEIGHT / 2)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(BAR_HEIGHT)
            .clip(shape)
            .background(SakuTheme.colors.surfaceAlt),
    ) {
        if (permille > 0) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(permille.coerceIn(0, PERMILLE_WHOLE) / PERMILLE_WHOLE.toFloat())
                    .clip(shape)
                    .background(color),
            )
        }
    }
}

private val BAR_HEIGHT = 6.dp

@SakuPreview
@Composable
private fun RankedRowPreview() {
    SakuPreviewSurface(padded = false) {
        Column(modifier = Modifier.width(360.dp)) {
            RankedRow(ColorSlot(0), "Pemberian", Money(4_770_000), sharePercent = 34, barPermille = 1_000)
            RankedRow(ColorSlot(1), "Gadget", Money(2_805_000), sharePercent = 20, barPermille = 588)
            RankedRow(
                colorSlot = ColorSlot(2),
                name = "Cash",
                value = Money(-1_038_500),
                supporting = "Below zero. Set an opening balance",
                supportingColor = SakuTheme.colors.warning,
                valueColor = SakuTheme.colors.warning,
            )
            RankedRow(null, "Other", Money(1_200_000), sharePercent = 8, barPermille = 252)
        }
    }
}
