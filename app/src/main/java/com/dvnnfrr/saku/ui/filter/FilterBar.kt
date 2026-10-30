package com.dvnnfrr.saku.ui.filter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dvnnfrr.saku.R
import com.dvnnfrr.saku.domain.TransactionFilter
import com.dvnnfrr.saku.ui.shared.EmptyState
import com.dvnnfrr.saku.ui.theme.SakuPreview
import com.dvnnfrr.saku.ui.theme.SakuPreviewSurface
import com.dvnnfrr.saku.ui.theme.SakuTheme

/**
 * The "Filtered" bar, directly under a period header while any tab is narrowed.
 *
 * It names what is in force rather than only marking that something is. A badge on the
 * icon is too small to stop a filtered total being read as a whole one, and recolouring
 * the figures would say it by colour alone. Renders nothing while the filter is inactive.
 */
@Composable
fun FilterBar(
    filter: TransactionFilter,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!filter.isActive) return
    val colors = SakuTheme.colors
    val spacing = SakuTheme.spacing

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.accentWash)
            .padding(start = spacing.screen, end = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Icon(
            imageVector = Icons.Filled.FilterList,
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = stringResource(R.string.filter_bar, filterSummary(filter)),
            style = SakuTheme.typography.label,
            color = colors.ink,
            modifier = Modifier.weight(1f),
        )
        val clearDescription = stringResource(R.string.filter_clear_description)
        // "Clear" alone, heard after the summary, does not say what it clears.
        TextButton(
            onClick = onClear,
            modifier = Modifier.semantics { contentDescription = clearDescription },
        ) {
            Text(
                text = stringResource(R.string.filter_clear),
                style = SakuTheme.typography.bodyStrong,
                color = colors.accent,
            )
        }
    }
}

/**
 * The filter icon for a header's trailing slot. While a filter is active it carries a dot
 * and is announced as "Filter, active", so the state is not carried by the dot alone.
 */
@Composable
fun FilterAction(
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SakuTheme.colors
    val description = stringResource(if (active) R.string.filter_action_active else R.string.filter_action)

    IconButton(onClick = onClick, modifier = modifier.semantics { contentDescription = description }) {
        BadgedBox(badge = { if (active) Badge(containerColor = colors.accent) }) {
            Icon(
                imageVector = Icons.Filled.FilterList,
                contentDescription = null,
                tint = if (active) colors.accent else colors.ink2,
            )
        }
    }
}

/**
 * What Trans, Stats and search show when the filter leaves nothing, in place of the plain
 * no-data state. It says the filter is the reason and offers the way out, so an empty
 * period is not mistaken for an unlogged one.
 */
@Composable
fun FilteredEmptyState(
    message: String,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EmptyState(
        icon = Icons.Filled.FilterList,
        message = message,
        modifier = modifier,
        action = {
            TextButton(onClick = onClear) {
                Text(
                    text = stringResource(R.string.filter_clear_description),
                    style = SakuTheme.typography.bodyStrong,
                    color = SakuTheme.colors.accent,
                )
            }
        },
    )
}

@SakuPreview
@Composable
private fun FilterBarPreview() {
    SakuPreviewSurface(padded = false) {
        FilterBar(filter = TransactionFilter(accountIds = setOf(1L, 2L)), onClear = {})
    }
}
