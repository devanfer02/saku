package com.dvnnfrr.saku.ui.shell

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.dvnnfrr.saku.R
import com.dvnnfrr.saku.domain.ThemePreference
import com.dvnnfrr.saku.ui.theme.SakuPreview
import com.dvnnfrr.saku.ui.theme.SakuPreviewSurface
import com.dvnnfrr.saku.ui.theme.SakuTheme

@StringRes
private fun labelFor(preference: ThemePreference): Int = when (preference) {
    ThemePreference.FOLLOW_SYSTEM -> R.string.appearance_follow_system
    ThemePreference.LIGHT -> R.string.appearance_light
    ThemePreference.DARK -> R.string.appearance_dark
}

/**
 * The whole appearance setting: three options, applied on tap.
 *
 * No confirm button. The change is instant, visible and reversible in one more tap,
 * so asking the user to confirm a colour they can already see would be ceremony.
 */
@Composable
fun AppearanceDialog(
    selected: ThemePreference,
    onSelect: (ThemePreference) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.more_appearance)) },
        text = {
            Column(Modifier.selectableGroup()) {
                ThemePreference.entries.forEach { option ->
                    Row(
                        selected = option == selected,
                        label = stringResource(labelFor(option)),
                        onSelect = { onSelect(option) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) }
        },
    )
}

@Composable
private fun Row(selected: Boolean, label: String, onSelect: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            // selectable on the row, not just the button, so the whole line is a
            // target and the radio state reaches the accessibility node once.
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = SakuTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = label,
            style = SakuTheme.typography.body,
            color = SakuTheme.colors.ink,
            modifier = Modifier.padding(start = SakuTheme.spacing.sm),
        )
    }
}

@SakuPreview
@Composable
private fun AppearanceDialogPreview() {
    SakuPreviewSurface {
        AppearanceDialog(
            selected = ThemePreference.FOLLOW_SYSTEM,
            onSelect = {},
            onDismiss = {},
        )
    }
}
