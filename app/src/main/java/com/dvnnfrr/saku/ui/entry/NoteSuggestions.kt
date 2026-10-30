package com.dvnnfrr.saku.ui.entry

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import com.dvnnfrr.saku.R
import com.dvnnfrr.saku.domain.Category
import com.dvnnfrr.saku.domain.CategoryKind
import com.dvnnfrr.saku.domain.ColorSlot
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.ui.shared.ColorSwatch
import com.dvnnfrr.saku.ui.shared.MoneyText
import com.dvnnfrr.saku.ui.theme.SakuPreview
import com.dvnnfrr.saku.ui.theme.SakuPreviewSurface
import com.dvnnfrr.saku.ui.theme.SakuTheme
import com.dvnnfrr.saku.ui.theme.formatIdr

/**
 * Past notes under the Note field. Stateless: the list and the pick come from outside.
 *
 * A plain column inside the sheet's scroll, not a popup. A popup window takes
 * accessibility focus and can take the keyboard's focus with it; a column keeps the
 * field focused and puts the rows right after it in TalkBack's reading order. Rows are
 * clickable but not focus-taking in touch mode, so the keyboard stays up while they show.
 */
@Composable
fun NoteSuggestions(
    suggestions: List<NoteSuggestion>,
    onPick: (NoteSuggestion) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (suggestions.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(color = SakuTheme.colors.line)
        suggestions.forEach { suggestion ->
            SuggestionRow(suggestion = suggestion, onClick = { onPick(suggestion) })
        }
    }
}

@Composable
private fun SuggestionRow(suggestion: NoteSuggestion, onClick: () -> Unit) {
    val colors = SakuTheme.colors
    val spacing = SakuTheme.spacing
    val amount = formatIdr(suggestion.lastAmount)
    val category = suggestion.category
    val spoken = if (category != null) {
        stringResource(R.string.entry_suggestion_spoken, suggestion.note, category.name, amount)
    } else {
        stringResource(R.string.entry_suggestion_spoken_no_category, suggestion.note, amount)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            // One announcement per row instead of three fragments. Role and action are
            // set again here so the row stays a button whichever way the two merge.
            .clearAndSetSemantics {
                contentDescription = spoken
                role = Role.Button
                onClick { onClick(); true }
            }
            .heightIn(min = spacing.touchTarget)
            .padding(vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        if (category != null) {
            ColorSwatch(category.colorSlot)
        } else {
            Box(modifier = Modifier.size(spacing.swatch))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = suggestion.note,
                style = SakuTheme.typography.body,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (category != null) {
                Text(
                    text = category.name,
                    style = SakuTheme.typography.caption,
                    color = colors.ink2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        MoneyText(
            money = suggestion.lastAmount,
            style = SakuTheme.typography.body,
            color = colors.ink2,
        )
    }
}

@SakuPreview
@Composable
private fun NoteSuggestionsPreview() {
    val kendaraan = Category(1, "Kendaraan Pribadi", CategoryKind.EXPENSE, ColorSlot(3), 0, archived = false)
    val gereja = Category(2, "Pemberian", CategoryKind.EXPENSE, ColorSlot(0), 1, archived = false)
    SakuPreviewSurface {
        NoteSuggestions(
            suggestions = listOf(
                NoteSuggestion("bensin motor", "bensin motor", kendaraan, Money(30_000)),
                NoteSuggestion("donasi gereja", "donasi gereja", gereja, Money(100_000)),
                NoteSuggestion("beli galon", "beli galon", null, Money(20_000)),
            ),
            onPick = {},
        )
    }
}
