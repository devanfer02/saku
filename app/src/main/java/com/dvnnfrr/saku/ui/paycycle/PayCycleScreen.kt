package com.dvnnfrr.saku.ui.paycycle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dvnnfrr.saku.R
import com.dvnnfrr.saku.ui.theme.SakuPreview
import com.dvnnfrr.saku.ui.theme.SakuPreviewSurface
import com.dvnnfrr.saku.ui.theme.SakuTheme
import kotlinx.datetime.LocalDate

private const val DAYS_IN_LONGEST_MONTH = 31
private const val DAYS_PER_ROW = 7

@Composable
fun PayCycleScreen(
    modifier: Modifier = Modifier,
    viewModel: PayCycleViewModel = viewModel(factory = PayCycleViewModel.Factory),
) {
    val state by viewModel.state.collectAsState()

    PayCycleScreen(
        state = state,
        onSelectDay = viewModel::selectStartDay,
        onClear = viewModel::clear,
        modifier = modifier,
    )
}

/**
 * The start day as a grid of 1 to 31, the current and previous pay months it gives, and a
 * way back to calendar months.
 *
 * A grid rather than a number field: every value is valid, so there is nothing to type
 * wrong, and 29 to 31 sit in plain sight with the note on what they do in short months.
 */
@Composable
fun PayCycleScreen(
    state: PayCycleUiState,
    onSelectDay: (Int) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SakuTheme.colors
    val spacing = SakuTheme.spacing

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(spacing.screen),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        Text(
            text = stringResource(R.string.pay_cycle_intro),
            style = SakuTheme.typography.body,
            color = colors.ink2,
        )

        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Text(
                text = stringResource(R.string.pay_cycle_start_day),
                style = SakuTheme.typography.bodyStrong,
                color = colors.ink,
            )
            DayGrid(selected = state.startDay, onSelect = onSelectDay)
            Text(
                text = stringResource(R.string.pay_cycle_short_month_note),
                style = SakuTheme.typography.caption,
                color = colors.ink2,
            )
        }

        if (state.current == null || state.previous == null) {
            Text(
                text = stringResource(R.string.pay_cycle_unset),
                style = SakuTheme.typography.body,
                color = colors.ink2,
            )
        } else {
            Preview(title = stringResource(R.string.pay_cycle_current), preview = state.current)
            Preview(title = stringResource(R.string.pay_cycle_previous), preview = state.previous)
            OutlinedButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.pay_cycle_clear), color = colors.ink)
            }
        }
    }
}

@Composable
private fun DayGrid(selected: Int?, onSelect: (Int) -> Unit) {
    val spacing = SakuTheme.spacing

    Column(
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        (1..DAYS_IN_LONGEST_MONTH).chunked(DAYS_PER_ROW).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                week.forEach { day ->
                    DayCell(day = day, selected = day == selected, onClick = { onSelect(day) })
                }
                // Pads the last row so its cells keep the width of the rows above.
                repeat(DAYS_PER_ROW - week.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun RowScope.DayCell(
    day: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = SakuTheme.colors
    val description = stringResource(R.string.pay_cycle_day_description, day)

    Box(
        modifier = Modifier
            .weight(1f)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(SakuTheme.spacing.sm))
            .background(if (selected) colors.accent else colors.surface)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = day.toString(),
            style = SakuTheme.typography.label,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else colors.ink,
        )
    }
}

@Composable
private fun Preview(title: String, preview: PayMonthPreview) {
    val colors = SakuTheme.colors
    val spacing = SakuTheme.spacing

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(spacing.corner))
            .padding(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Text(text = title, style = SakuTheme.typography.caption, color = colors.ink2)
        Text(text = preview.label, style = SakuTheme.typography.bodyStrong, color = colors.ink)
        Text(text = preview.span, style = SakuTheme.typography.body, color = colors.ink2)
    }
}

@SakuPreview
@Composable
private fun PayCycleSetPreview() {
    SakuPreviewSurface(padded = false) {
        PayCycleScreen(
            state = assemblePayCycle(startDay = 30, today = LocalDate(2026, 10, 30)),
            onSelectDay = {},
            onClear = {},
        )
    }
}

@SakuPreview
@Composable
private fun PayCycleUnsetPreview() {
    SakuPreviewSurface(padded = false) {
        PayCycleScreen(state = PayCycleUiState(), onSelectDay = {}, onClear = {})
    }
}
