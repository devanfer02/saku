package com.dvnnfrr.saku.ui.entry

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.dvnnfrr.saku.R
import com.dvnnfrr.saku.domain.TxnType
import com.dvnnfrr.saku.receipt.itemSearchName
import com.dvnnfrr.saku.ui.theme.SakuTheme

/**
 * The item a saved expense was for, to open its price history on: the note without its
 * quantity.
 * Null for a new entry, any other type, or a blank note, where there is no item to follow.
 */
fun priceHistoryItem(form: EntryFormState): String? {
    val savedExpense = form.isEditing && form.type == TxnType.EXPENSE
    if (!savedExpense) return null
    return itemSearchName(form.note).ifEmpty { null }
}

/** Price history, just above the keypad so a thumb reaches it. */
@Composable
fun PriceHistoryAction(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = SakuTheme.colors
    val spacing = SakuTheme.spacing
    Box(modifier = modifier.padding(horizontal = spacing.screen)) {
        TextButton(onClick = onClick, modifier = Modifier.heightIn(min = spacing.touchTarget)) {
            Icon(Icons.AutoMirrored.Filled.ShowChart, contentDescription = null, tint = colors.accent)
            Text(
                text = stringResource(R.string.entry_price_history),
                style = SakuTheme.typography.bodyStrong,
                color = colors.accent,
                modifier = Modifier.padding(start = spacing.sm),
            )
        }
    }
}
