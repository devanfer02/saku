package com.dvnnfrr.saku.ui.entry

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dvnnfrr.saku.R
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.ui.theme.SakuTheme
import com.dvnnfrr.saku.ui.theme.formatIdr

/**
 * The Scan receipt action, just above the keypad so it sits under the thumb. One tap opens
 * a two-item menu; there is no receipt-or-screenshot choice, since one reading handles both.
 */
@Composable
fun ScanReceiptAction(
    reading: Boolean,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SakuTheme.colors
    var menuOpen by rememberSaveable { mutableStateOf(false) }

    Box(modifier = modifier.padding(horizontal = SakuTheme.spacing.screen)) {
        TextButton(
            onClick = { menuOpen = true },
            enabled = !reading,
            modifier = Modifier.heightIn(min = SakuTheme.spacing.touchTarget),
        ) {
            if (reading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = colors.accent,
                )
            } else {
                Icon(Icons.Outlined.ReceiptLong, contentDescription = null, tint = colors.accent)
            }
            Text(
                text = stringResource(if (reading) R.string.entry_scan_reading else R.string.entry_scan_receipt),
                style = SakuTheme.typography.bodyStrong,
                color = if (reading) colors.ink2 else colors.accent,
                modifier = Modifier.padding(start = SakuTheme.spacing.sm),
            )
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            containerColor = colors.surface,
        ) {
            Text(
                text = stringResource(R.string.entry_scan_both_kinds),
                style = SakuTheme.typography.caption,
                color = colors.ink2,
                modifier = Modifier.padding(horizontal = SakuTheme.spacing.md, vertical = SakuTheme.spacing.xs),
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.entry_scan_camera), color = colors.ink) },
                leadingIcon = { Icon(Icons.Outlined.PhotoCamera, contentDescription = null, tint = colors.ink2) },
                onClick = { menuOpen = false; onCamera() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.entry_scan_gallery), color = colors.ink) },
                leadingIcon = { Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, tint = colors.ink2) },
                onClick = { menuOpen = false; onGallery() },
            )
        }
    }
}

/** The other amounts a scan read, under the amount. Tapping one makes it the amount. */
@Composable
fun ReceiptAmountChips(
    amounts: List<Money>,
    totalFound: Boolean,
    onPick: (Money) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SakuTheme.colors
    val spacing = SakuTheme.spacing
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = spacing.screen)) {
        if (!totalFound) {
            Text(
                text = stringResource(R.string.entry_scan_no_total),
                style = SakuTheme.typography.caption,
                color = colors.ink2,
            )
        }
        if (amounts.isEmpty()) return@Column
        Text(
            text = stringResource(R.string.entry_scan_other_amounts),
            style = SakuTheme.typography.caption,
            color = colors.ink2,
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            amounts.forEach { amount ->
                val label = formatIdr(amount, withPrefix = true)
                val spoken = stringResource(R.string.entry_scan_chip_spoken, label)
                SuggestionChip(
                    onClick = { onPick(amount) },
                    label = { Text(label, style = SakuTheme.typography.body) },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = colors.surfaceAlt,
                        labelColor = colors.ink,
                    ),
                    border = SuggestionChipDefaults.suggestionChipBorder(enabled = true, borderColor = colors.line),
                    modifier = Modifier.semantics { contentDescription = spoken },
                )
            }
        }
    }
}

/** Lines a scan read but did not use. Tapping one adds it to the note and drops it here. */
@Composable
fun ReceiptLines(
    lines: List<String>,
    onAdd: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (lines.isEmpty()) return
    val colors = SakuTheme.colors
    val spacing = SakuTheme.spacing
    Column(modifier = modifier.fillMaxWidth().padding(top = spacing.sm)) {
        Text(
            text = stringResource(R.string.entry_scan_lines_title),
            style = SakuTheme.typography.bodyStrong,
            color = colors.ink,
        )
        Text(
            text = stringResource(R.string.entry_scan_lines_hint),
            style = SakuTheme.typography.caption,
            color = colors.ink2,
        )
        lines.forEach { line ->
            val spoken = stringResource(R.string.entry_scan_line_spoken, line)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = spacing.touchTarget)
                    .clickable(role = Role.Button) { onAdd(line) }
                    .semantics(mergeDescendants = true) { contentDescription = spoken },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = line,
                    style = SakuTheme.typography.body,
                    color = colors.ink,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Outlined.Add, contentDescription = null, tint = colors.ink2)
            }
        }
    }
}
