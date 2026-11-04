package com.dvnnfrr.saku.ui.receipt

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dvnnfrr.saku.R
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.ui.shared.MoneyText
import com.dvnnfrr.saku.ui.theme.SakuTheme
import com.dvnnfrr.saku.ui.theme.formatIdr
import com.dvnnfrr.saku.ui.transactions.dateLabel

/** Store, date, photo count, the total, where it was read from, its chips, and the check against the items. */
@Composable
internal fun ReceiptSummary(state: ReceiptUiState, onPickTotal: (Money) -> Unit) {
    val spacing = SakuTheme.spacing
    val colors = SakuTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.screen, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        // The store when one was read; else the app it came from ("GoFood"), since an
        // order screen often shows no restaurant name; else a plain "Receipt", not a
        // "not found" that reads like an error.
        val heading = state.store ?: state.source
        Text(
            text = heading ?: stringResource(R.string.receipt_unnamed),
            style = SakuTheme.typography.title,
            color = if (heading != null) colors.ink else colors.ink2,
            modifier = Modifier.semantics { heading() },
        )
        val photos = androidx.compose.ui.res.pluralStringResource(R.plurals.receipt_photos, state.photos, state.photos)
        Text(
            text = listOfNotNull(state.date?.dateLabel(), photos).joinToString(" · "),
            style = SakuTheme.typography.caption,
            color = colors.ink2,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.receipt_total),
                style = SakuTheme.typography.body,
                color = colors.ink2,
                modifier = Modifier.weight(1f),
            )
            val shown = state.total ?: state.oneRowAmount
            if (shown != null) MoneyText(shown, style = SakuTheme.typography.amountLarge)
        }
        if (state.total == null) {
            Text(stringResource(R.string.receipt_no_total), style = SakuTheme.typography.caption, color = colors.ink2)
        }
        state.source?.let { source ->
            Text(stringResource(R.string.receipt_source, source), style = SakuTheme.typography.caption, color = colors.ink2)
        }
        ReceiptAmountChips(amounts = state.chips, onPick = onPickTotal)
        state.check?.let { CheckLine(it) }
    }
}

/** The items against the total: a tick when they match, else what is missing or extra. */
@Composable
private fun CheckLine(check: ReceiptCheck) {
    val colors = SakuTheme.colors
    val difference = check.difference
    val message = when {
        check.matches -> stringResource(R.string.receipt_check_match)
        difference.isPositive -> stringResource(R.string.receipt_check_short, formatIdr(difference))
        else -> stringResource(R.string.receipt_check_over, formatIdr(difference.absolute))
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = SakuTheme.spacing.sm)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (check.matches) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = if (check.matches) colors.income else colors.warning,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = message,
                style = SakuTheme.typography.bodyStrong,
                color = colors.ink,
                modifier = Modifier.padding(start = SakuTheme.spacing.sm),
            )
        }
        if (!check.matches) {
            Text(
                text = stringResource(R.string.receipt_check_detail, formatIdr(check.itemsNet), formatIdr(check.total)),
                style = SakuTheme.typography.caption,
                color = colors.ink2,
            )
        }
    }
}

/** The other amounts a scan read, under the total. Tapping one makes it the total. */
@Composable
internal fun ReceiptAmountChips(amounts: List<Money>, onPick: (Money) -> Unit) {
    if (amounts.isEmpty()) return
    val colors = SakuTheme.colors
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.receipt_other_amounts), style = SakuTheme.typography.caption, color = colors.ink2)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(SakuTheme.spacing.sm),
        ) {
            amounts.forEach { amount ->
                val label = formatIdr(amount, withPrefix = true)
                val spoken = stringResource(R.string.receipt_chip_spoken, label)
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

@Composable
internal fun ReadingRow(photo: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = SakuTheme.spacing.screen, vertical = SakuTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = SakuTheme.colors.accent)
        Text(
            text = stringResource(R.string.receipt_reading_part, photo),
            style = SakuTheme.typography.body,
            color = SakuTheme.colors.ink2,
            modifier = Modifier
                .padding(start = SakuTheme.spacing.sm)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/** A warning that needs the user: a part may be missing, or a photo could not be read. */
@Composable
internal fun Notice(text: String) {
    val colors = SakuTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SakuTheme.spacing.screen, vertical = SakuTheme.spacing.xs)
            .border(1.dp, colors.warning, RoundedCornerShape(SakuTheme.spacing.corner))
            .padding(SakuTheme.spacing.md)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = colors.warning, modifier = Modifier.size(20.dp))
        Text(
            text = text,
            style = SakuTheme.typography.body,
            color = colors.ink,
            modifier = Modifier.padding(start = SakuTheme.spacing.sm),
        )
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Text(
        text = text,
        style = SakuTheme.typography.label,
        color = SakuTheme.colors.ink2,
        modifier = Modifier
            .padding(horizontal = SakuTheme.spacing.screen, vertical = SakuTheme.spacing.sm)
            .semantics { heading() },
    )
}

/**
 * One item. TalkBack hears one node, "MINYAK GORENG 2L, Rp 38.900, Makan & Minum", and a
 * double tap opens its category choice.
 */
@Composable
internal fun ItemRow(item: ReceiptItemUi, onClick: () -> Unit) {
    val spacing = SakuTheme.spacing
    val colors = SakuTheme.colors
    val category = item.categoryName ?: stringResource(R.string.receipt_item_spoken_unset)
    val spoken = stringResource(R.string.receipt_item_spoken, item.note, formatIdr(item.amount), category)
    val changeLabel = stringResource(R.string.receipt_item_change)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = spacing.rowMinHeight)
            .clickable(onClickLabel = changeLabel, role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = spoken
                role = Role.Button
                onClick(label = changeLabel) { onClick(); true }
            }
            .padding(horizontal = spacing.screen, vertical = spacing.sm),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = spacing.md)) {
            Text(item.name, style = SakuTheme.typography.bodyStrong, color = colors.ink)
            if (item.quantity > 1) {
                val unit = Money(item.price.rupiah / item.quantity)
                Text(
                    text = stringResource(R.string.receipt_item_quantity, item.quantity, formatIdr(unit)),
                    style = SakuTheme.typography.caption,
                    color = colors.ink2,
                )
            }
            if (item.discount.isPositive) {
                Text(
                    text = stringResource(R.string.receipt_item_discount, formatIdr(item.discount)),
                    style = SakuTheme.typography.caption,
                    color = colors.ink2,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            MoneyText(item.amount, style = SakuTheme.typography.amountSmall)
            if (item.categoryName != null) {
                Text(
                    text = item.categoryName,
                    style = SakuTheme.typography.caption,
                    color = colors.ink,
                    textAlign = TextAlign.End,
                )
                if (item.fromHistory) {
                    Text(
                        text = stringResource(R.string.receipt_item_history),
                        style = SakuTheme.typography.caption,
                        color = colors.ink2,
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.receipt_item_choose),
                    style = SakuTheme.typography.bodyStrong,
                    color = colors.accent,
                )
            }
        }
    }
}

/** The switch to save what the items do not cover as one row, and that row's category. */
@Composable
internal fun GapRowControls(state: ReceiptUiState, onGapRow: (Boolean) -> Unit, onOpenGapCategory: () -> Unit) {
    val spacing = SakuTheme.spacing
    val colors = SakuTheme.colors
    Column(modifier = Modifier.fillMaxWidth().padding(top = spacing.sm)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = spacing.touchTarget)
                .toggleable(value = state.gapRow, role = Role.Switch, onValueChange = onGapRow)
                .padding(horizontal = spacing.screen),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.receipt_gap_row), style = SakuTheme.typography.body, color = colors.ink)
                state.gap?.let { MoneyText(it, style = SakuTheme.typography.caption, color = colors.ink2) }
            }
            Switch(
                checked = state.gapRow,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = colors.accent,
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
        if (state.gapRow) {
            PickRow(
                label = stringResource(R.string.receipt_gap_category),
                value = state.categoryName(state.gapCategoryId),
                onClick = onOpenGapCategory,
            )
        }
    }
}

/** One row: its category, its note, and the lines Saku could not place, to add to the note. */
@Composable
internal fun OneRowFields(state: ReceiptUiState, onOpenCategory: () -> Unit, onAddLine: (String) -> Unit) {
    val spacing = SakuTheme.spacing
    val colors = SakuTheme.colors
    Column(modifier = Modifier.fillMaxWidth()) {
        PickRow(
            label = stringResource(R.string.receipt_category),
            value = state.categoryName(state.oneRowCategoryId),
            onClick = onOpenCategory,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = spacing.touchTarget)
                .padding(horizontal = spacing.screen)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.receipt_note), style = SakuTheme.typography.body, color = colors.ink2, modifier = Modifier.weight(1f))
            Text(state.oneRowNote, style = SakuTheme.typography.body, color = colors.ink, textAlign = TextAlign.End, modifier = Modifier.weight(2f))
        }
        if (state.unplaced.isEmpty()) return@Column
        SectionTitle(stringResource(R.string.receipt_lines_title))
        Text(
            text = stringResource(R.string.receipt_lines_hint),
            style = SakuTheme.typography.caption,
            color = colors.ink2,
            modifier = Modifier.padding(horizontal = spacing.screen),
        )
        state.unplaced.forEach { line ->
            val spoken = stringResource(R.string.receipt_line_spoken, line)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = spacing.touchTarget)
                    .clickable(role = Role.Button) { onAddLine(line) }
                    .semantics(mergeDescendants = true) { contentDescription = spoken }
                    .padding(horizontal = spacing.screen),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(line, style = SakuTheme.typography.body, color = colors.ink, modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.Add, contentDescription = null, tint = colors.ink2)
            }
        }
    }
}

/** A label and its value or Choose, opening a choice. */
@Composable
private fun PickRow(label: String, value: String?, onClick: () -> Unit) {
    val spacing = SakuTheme.spacing
    val colors = SakuTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = spacing.touchTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = spacing.screen),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = SakuTheme.typography.body, color = colors.ink2, modifier = Modifier.weight(1f))
        Text(
            text = value ?: stringResource(R.string.receipt_item_choose),
            style = SakuTheme.typography.bodyStrong,
            color = if (value != null) colors.ink else colors.accent,
            textAlign = TextAlign.End,
        )
    }
}

/** An Account or Tags field in the bottom bar: a small label over its value. */
@Composable
internal fun BarField(label: String, value: String, spoken: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val spacing = SakuTheme.spacing
    val colors = SakuTheme.colors
    Column(
        modifier = modifier
            .heightIn(min = spacing.touchTarget)
            .border(1.dp, colors.line, RoundedCornerShape(spacing.corner))
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = spoken
                role = Role.Button
                onClick { onClick(); true }
            }
            .padding(horizontal = spacing.md, vertical = spacing.xs),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(label, style = SakuTheme.typography.caption, color = colors.ink2)
        Text(value, style = SakuTheme.typography.bodyStrong, color = colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
