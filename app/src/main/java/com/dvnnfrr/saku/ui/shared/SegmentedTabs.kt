package com.dvnnfrr.saku.ui.shared

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dvnnfrr.saku.ui.theme.SakuPreview
import com.dvnnfrr.saku.ui.theme.SakuPreviewSurface
import com.dvnnfrr.saku.ui.theme.SakuTheme

/**
 * A row of mutually exclusive options.
 *
 * Three consumers: the entry sheet's type selector, the category screen's kind tabs and
 * the stats income/expense tabs — plus the shell's Daily/Calendar split.
 *
 * Selection is exposed to semantics through [Modifier.selectable] with [Role.Tab], not
 * only as a background colour. An end-to-end check asserts `selected: true` on a tab to
 * prove state survived navigation, and a purely visual selection would leave that
 * assertion with nothing to read.
 */
@Composable
fun SegmentedTabs(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SakuTheme.colors
    val spacing = SakuTheme.spacing
    val shape = RoundedCornerShape(spacing.corner)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surfaceAlt)
            .padding(spacing.xs)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            val background by animateColorAsState(
                targetValue = if (selected) colors.surface else colors.surfaceAlt,
                label = "segmentBackground",
            )
            val content by animateColorAsState(
                targetValue = if (selected) colors.accent else colors.ink2,
                label = "segmentContent",
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(spacing.sm))
                    .background(background)
                    .selectable(
                        selected = selected,
                        role = Role.Tab,
                        onClick = { onSelect(index) },
                    )
                    .defaultMinSize(minHeight = 40.dp)
                    .padding(horizontal = spacing.sm, vertical = spacing.sm),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = option,
                    style = SakuTheme.typography.label,
                    color = content,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@SakuPreview
@Composable
private fun SegmentedTabsPreview() {
    SakuPreviewSurface {
        Column(
            modifier = Modifier.width(360.dp),
            verticalArrangement = Arrangement.spacedBy(SakuTheme.spacing.md),
        ) {
            var typeIndex by remember { mutableIntStateOf(0) }
            SegmentedTabs(
                options = listOf("Expense", "Income", "Transfer"),
                selectedIndex = typeIndex,
                onSelect = { typeIndex = it },
            )
            SegmentedTabs(
                options = listOf("Daily", "Calendar"),
                selectedIndex = 1,
                onSelect = {},
            )
        }
    }
}
