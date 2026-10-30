package com.dvnnfrr.saku.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dvnnfrr.saku.domain.ColorSlot
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.TxnType

/**
 * Every token in the active theme, on one screen.
 *
 * Previewed in both themes so a colour defined for only one set is visible as a hole
 * rather than discovered on a device. Not a destination — it exists for the preview pane
 * and for a future screenshot harness to point at.
 */
@Composable
fun TokenReference(modifier: Modifier = Modifier) {
    val colors = SakuTheme.colors
    val spacing = SakuTheme.spacing

    Column(
        modifier = modifier
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(spacing.screen),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        Text("Surfaces", style = SakuTheme.typography.title, color = colors.ink)
        Swatch("background", colors.background)
        Swatch("surface", colors.surface)
        Swatch("surfaceAlt", colors.surfaceAlt)
        Swatch("line", colors.line)

        Text("Ink", style = SakuTheme.typography.title, color = colors.ink)
        Swatch("ink", colors.ink)
        Swatch("ink2", colors.ink2)
        Swatch("ink3", colors.ink3)

        Text("Accent", style = SakuTheme.typography.title, color = colors.ink)
        Swatch("accent", colors.accent)
        Swatch("accentWash", colors.accentWash)

        Text("Direction", style = SakuTheme.typography.title, color = colors.ink)
        Swatch("income", colors.income)
        Swatch("expense", colors.expense)
        Swatch("transfer", colors.transfer)

        Text("Chart slots", style = SakuTheme.typography.title, color = colors.ink)
        for (index in 0 until ColorSlot.COUNT) {
            Swatch("slot ${index + 1}", colors.forSlot(ColorSlot(index)))
        }

        Text("Type scale", style = SakuTheme.typography.title, color = colors.ink)
        Text("Display 32", style = SakuTheme.typography.display, color = colors.ink)
        Text("Title 22", style = SakuTheme.typography.title, color = colors.ink)
        Text("Heading 17", style = SakuTheme.typography.heading, color = colors.ink)
        Text("Body 15", style = SakuTheme.typography.body, color = colors.ink)
        Text("Body strong 15", style = SakuTheme.typography.bodyStrong, color = colors.ink)
        Text("Label 13", style = SakuTheme.typography.label, color = colors.ink2)
        Text("Caption 12", style = SakuTheme.typography.caption, color = colors.ink2)

        Text("Amounts", style = SakuTheme.typography.title, color = colors.ink)
        Text(
            text = formatIdr(Money(5_061_400)),
            style = SakuTheme.typography.amountLarge,
            color = colors.forDirection(TxnType.INCOME),
        )
        Text(
            text = formatIdr(Money(5_061_400)),
            style = SakuTheme.typography.amountMedium,
            color = colors.forDirection(TxnType.EXPENSE),
        )
        Text(
            text = formatIdr(Money(5_061_400), withPrefix = false),
            style = SakuTheme.typography.amountSmall,
            color = colors.forDirection(TxnType.TRANSFER),
        )
    }
}

@Composable
private fun Swatch(name: String, color: Color) {
    val colors = SakuTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SakuTheme.spacing.md),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(color, RoundedCornerShape(SakuTheme.spacing.sm))
                .border(1.dp, colors.line, RoundedCornerShape(SakuTheme.spacing.sm)),
        )
        Text(name, style = SakuTheme.typography.body, color = colors.ink)
    }
}

@SakuPreview
@Composable
private fun TokenReferencePreview() {
    SakuTheme {
        TokenReference()
    }
}
