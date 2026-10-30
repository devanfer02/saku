package com.dvnnfrr.saku.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dvnnfrr.saku.R
import com.dvnnfrr.saku.domain.DateRange
import com.dvnnfrr.saku.domain.PeriodMode
import com.dvnnfrr.saku.ui.shared.periodLabel
import com.dvnnfrr.saku.ui.theme.SakuPreview
import com.dvnnfrr.saku.ui.theme.SakuPreviewSurface
import com.dvnnfrr.saku.ui.theme.SakuTheme
import kotlinx.datetime.LocalDate
import java.util.Locale

/**
 * What the period label's tap does in [mode], for TalkBack: "Choose a month". Null in
 * Period mode, where the label opens nothing; the Period tab keeps its range picker.
 */
internal fun periodPickerActionRes(mode: PeriodMode): Int? = when (mode) {
    PeriodMode.WEEKLY -> R.string.stats_pick_week
    PeriodMode.MONTHLY -> R.string.stats_pick_month
    PeriodMode.ANNUALLY -> R.string.stats_pick_year
    PeriodMode.CUSTOM -> null
}

/**
 * The picker the Stats header opens, matching the active mode: a date for a week, a month
 * grid under a year for a month, a year grid for a year. Each offers the period holding
 * [today]. [onPick] receives a range of [mode], built by the same `DateRange` functions the
 * arrows step with; the caller selects it and closes the picker.
 */
@Composable
fun PeriodPickerDialog(
    mode: PeriodMode,
    selected: DateRange,
    today: LocalDate,
    firstDataYear: Int?,
    onPick: (DateRange) -> Unit,
    onDismiss: () -> Unit,
) {
    when (mode) {
        PeriodMode.WEEKLY -> WeekPickerDialog(selected, today, onPick, onDismiss)
        PeriodMode.MONTHLY -> MonthPickerDialog(selected, today, onPick, onDismiss)
        PeriodMode.ANNUALLY -> YearPickerDialog(selected, today, firstDataYear, onPick, onDismiss)
        PeriodMode.CUSTOM -> Unit
    }
}

/**
 * A year with its arrows over the twelve months. The arrows are not bounded by history:
 * the header's own arrows reach an empty year, so the grid does too. Tapping a month
 * picks it at once; there is nothing to confirm.
 */
@Composable
private fun MonthPickerDialog(
    selected: DateRange,
    today: LocalDate,
    onPick: (DateRange) -> Unit,
    onDismiss: () -> Unit,
) {
    var year by rememberSaveable(selected) { mutableIntStateOf(selected.start.year) }
    val months = remember(year, selected, today) { monthChoices(year, selected, today) }

    PickerAlert(
        title = { YearHeader(year = year, onPrevious = { year -= 1 }, onNext = { year += 1 }) },
        todayLabel = stringResource(R.string.stats_this_month),
        onToday = { onPick(periodContaining(today, PeriodMode.MONTHLY)) },
        onDismiss = onDismiss,
    ) {
        ChoiceGrid(choices = months, describe = { periodLabel(it.range) }, onPick = onPick)
    }
}

/** Every year from the first with data through next year, three across. */
@Composable
private fun YearPickerDialog(
    selected: DateRange,
    today: LocalDate,
    firstDataYear: Int?,
    onPick: (DateRange) -> Unit,
    onDismiss: () -> Unit,
) {
    val years = remember(firstDataYear, selected, today) { yearChoices(firstDataYear, selected, today) }

    PickerAlert(
        title = { PickerTitle(stringResource(R.string.stats_pick_year)) },
        todayLabel = stringResource(R.string.stats_this_year),
        onToday = { onPick(periodContaining(today, PeriodMode.ANNUALLY)) },
        onDismiss = onDismiss,
    ) {
        ChoiceGrid(
            choices = years,
            describe = { it.label },
            onPick = onPick,
            modifier = Modifier.verticalScroll(rememberScrollState()),
        )
    }
}

/**
 * A calendar, and the Monday to Sunday week holding the picked day. Pinned to a UK
 * calendar so weeks start on Monday as they do everywhere else in the app, the same
 * reason [RangePickerDialog] gives. A day tap is not yet a week, so this one keeps Done.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeekPickerDialog(
    selected: DateRange,
    today: LocalDate,
    onPick: (DateRange) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = remember(selected) {
        DatePickerState(locale = Locale.UK, initialSelectedDateMillis = selected.start.toUtcMillis())
    }

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedDateMillis != null,
                onClick = {
                    state.selectedDateMillis?.toUtcDate()?.let { date ->
                        onPick(periodContaining(date, PeriodMode.WEEKLY))
                    }
                },
            ) { Text(stringResource(R.string.action_done)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onPick(periodContaining(today, PeriodMode.WEEKLY)) }) {
                    Text(stringResource(R.string.stats_this_week))
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    ) {
        DatePicker(
            state = state,
            title = { PickerTitle(stringResource(R.string.stats_pick_week), Modifier.padding(SakuTheme.spacing.lg)) },
        )
    }
}

/** The month and year pickers' frame: a title, the grid, "This ..." and Cancel. */
@Composable
private fun PickerAlert(
    title: @Composable () -> Unit,
    todayLabel: String,
    onToday: () -> Unit,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val colors = SakuTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        titleContentColor = colors.ink,
        textContentColor = colors.ink,
        title = title,
        text = content,
        confirmButton = {
            TextButton(onClick = onToday) {
                Text(text = todayLabel, color = colors.accent, style = SakuTheme.typography.bodyStrong)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel), color = colors.accent)
            }
        },
    )
}

@Composable
private fun PickerTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = SakuTheme.typography.title,
        color = SakuTheme.colors.ink,
        modifier = modifier.semantics { heading() },
    )
}

@Composable
private fun YearHeader(year: Int, onPrevious: () -> Unit, onNext: () -> Unit) {
    val colors = SakuTheme.colors
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.stats_previous_year),
                tint = colors.ink2,
            )
        }
        Text(
            text = year.toString(),
            style = SakuTheme.typography.title,
            color = colors.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        IconButton(onClick = onNext) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.stats_next_year),
                tint = colors.ink2,
            )
        }
    }
}

private const val CHOICE_COLUMNS = 3

/** Choices three across, so twelve months read as four rows. */
@Composable
private fun ChoiceGrid(
    choices: List<PeriodChoice>,
    describe: (PeriodChoice) -> String,
    onPick: (DateRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = SakuTheme.spacing
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        choices.chunked(CHOICE_COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                row.forEach { choice ->
                    ChoiceCell(
                        choice = choice,
                        description = describe(choice),
                        onClick = { onPick(choice.range) },
                        modifier = Modifier.weight(1f),
                    )
                }
                // A short last row keeps its cells the width of the rest.
                repeat(CHOICE_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * One month or year. Selected is a wash behind accent text, as the shared picker grid
 * draws it; current is an accent outline. Both are spoken, so neither rests on colour.
 */
@Composable
private fun ChoiceCell(
    choice: PeriodChoice,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SakuTheme.colors
    val shape = RoundedCornerShape(SakuTheme.spacing.corner)
    val spoken = if (choice.current) "$description, ${stringResource(R.string.stats_picker_current)}" else description

    Box(
        modifier = modifier
            .heightIn(min = SakuTheme.spacing.touchTarget)
            .clip(shape)
            .then(if (choice.selected) Modifier.background(colors.accentWash) else Modifier)
            .then(if (choice.current) Modifier.border(1.dp, colors.accent, shape) else Modifier)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = spoken
                selected = choice.selected
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = choice.label,
            style = if (choice.selected) SakuTheme.typography.bodyStrong else SakuTheme.typography.body,
            color = if (choice.selected) colors.accent else colors.ink,
            maxLines = 1,
        )
    }
}

@SakuPreview
@Composable
private fun ChoiceGridPreview() {
    val today = LocalDate(2026, 10, 30)
    SakuPreviewSurface {
        Column(verticalArrangement = Arrangement.spacedBy(SakuTheme.spacing.lg)) {
            YearHeader(year = 2026, onPrevious = {}, onNext = {})
            ChoiceGrid(
                choices = monthChoices(2026, DateRange.ofMonth(LocalDate(2026, 8, 1)), today),
                describe = { periodLabel(it.range) },
                onPick = {},
            )
            ChoiceGrid(
                choices = yearChoices(2025, DateRange.ofYear(2026), today),
                describe = { it.label },
                onPick = {},
            )
        }
    }
}
