package com.dvnnfrr.saku.ui.paycycle

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dvnnfrr.saku.R
import com.dvnnfrr.saku.domain.DateRange
import com.dvnnfrr.saku.domain.MonthView
import com.dvnnfrr.saku.ui.shared.SegmentedTabs
import com.dvnnfrr.saku.ui.shared.paySpanLabel
import com.dvnnfrr.saku.ui.theme.SakuPreview
import com.dvnnfrr.saku.ui.theme.SakuPreviewSurface
import com.dvnnfrr.saku.ui.theme.SakuTheme
import kotlinx.datetime.LocalDate

/**
 * Calendar month or Pay month, under the month selector on Trans and Stats.
 *
 * Lives with the pay cycle rather than in `ui/shared` because it carries its own copy,
 * which shared components may not.
 *
 * Draws nothing when [view] is null, which is how a screen hides it while no pay cycle is
 * set. In Pay month, [span] (when given) is written out beneath, so the pay month is never
 * read as the calendar month it is named after. Stats passes none because its own range
 * line already shows the span.
 */
@Composable
fun MonthViewSwitch(
    view: MonthView?,
    onSelect: (MonthView) -> Unit,
    modifier: Modifier = Modifier,
    span: DateRange? = null,
) {
    if (view == null) return
    val spacing = SakuTheme.spacing

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = spacing.md, vertical = spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SegmentedTabs(
            options = listOf(
                stringResource(R.string.month_view_calendar),
                stringResource(R.string.month_view_pay),
            ),
            selectedIndex = view.ordinal,
            onSelect = { onSelect(MonthView.entries[it]) },
        )
        if (view == MonthView.PAY && span != null) {
            Text(
                text = paySpanLabel(span),
                style = SakuTheme.typography.caption,
                color = SakuTheme.colors.ink2,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.padding(top = spacing.xs),
            )
        }
    }
}

@SakuPreview
@Composable
private fun MonthViewSwitchPreview() {
    SakuPreviewSurface(padded = false) {
        Column(modifier = Modifier.width(360.dp)) {
            MonthViewSwitch(
                view = MonthView.PAY,
                onSelect = {},
                span = DateRange.ofPayMonth(LocalDate(2026, 10, 30), 30),
            )
            MonthViewSwitch(view = MonthView.CALENDAR, onSelect = {})
        }
    }
}
