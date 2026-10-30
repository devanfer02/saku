package com.dvnnfrr.saku.ui.shared

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.TxnType
import com.dvnnfrr.saku.ui.theme.SakuPreview
import com.dvnnfrr.saku.ui.theme.SakuPreviewSurface
import com.dvnnfrr.saku.ui.theme.SakuTheme
import com.dvnnfrr.saku.ui.theme.forDirection

/**
 * One figure as a part of a whole: an arc on a track, the percentage inside, the figure
 * and its label beneath.
 *
 * Not a composition chart: there is one share here, with the number written out, so the
 * colour is never the only cue. Compositions are ranked bars ([RankedRow]). [percent] null draws the
 * bare track and no percentage, for a share of nothing.
 *
 * [contentDescription] replaces the parts for a screen reader, because "80%" heard on its
 * own does not say of what. Copy arrives from the caller, as everywhere in `ui/shared`.
 */
@Composable
fun ShareRing(
    label: String,
    amount: Money,
    percent: Int?,
    type: TxnType,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    strokeWidth: Dp = 8.dp,
) {
    val colors = SakuTheme.colors
    val track = colors.line
    val arc = colors.forDirection(type)

    Column(
        modifier = modifier.clearAndSetSemantics { this.contentDescription = contentDescription },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SakuTheme.spacing.xs),
    ) {
        Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(size)) {
                val stroke = strokeWidth.toPx()
                val inset = stroke / 2
                val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
                drawArc(
                    color = track,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke),
                )
                val share = percent?.coerceIn(0, 100) ?: 0
                if (share > 0) {
                    drawArc(
                        color = arc,
                        startAngle = -90f,
                        sweepAngle = 360f * share / 100f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Butt),
                    )
                }
            }
            if (percent != null) {
                Text(
                    text = "$percent%",
                    style = SakuTheme.typography.label,
                    color = colors.ink,
                    maxLines = 1,
                )
            }
        }
        Text(text = label, style = SakuTheme.typography.caption, color = colors.ink2, maxLines = 1)
        MoneyText(
            money = amount,
            type = type,
            style = SakuTheme.typography.amountSmall,
        )
    }
}

@SakuPreview
@Composable
private fun ShareRingPreview() {
    SakuPreviewSurface {
        Row(horizontalArrangement = Arrangement.spacedBy(SakuTheme.spacing.xl)) {
            ShareRing("Income", Money(8_000_000), 80, TxnType.INCOME, "Income 8.000.000, 80 percent")
            ShareRing("Expenses", Money(1_500_000), 25, TxnType.EXPENSE, "Expenses 1.500.000, 25 percent")
            ShareRing("Income", Money.ZERO, null, TxnType.INCOME, "Income 0")
        }
    }
}
