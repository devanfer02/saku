package com.dvnnfrr.saku.ui.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dvnnfrr.saku.ui.theme.SakuPreview
import com.dvnnfrr.saku.ui.theme.SakuPreviewSurface
import com.dvnnfrr.saku.ui.theme.SakuTheme

/**
 * The affected-count line, or null when there is no count to state.
 *
 * A merge or a batch reversal touches rows the user cannot see from the dialog, so the
 * number goes in the dialog rather than in the toast afterwards. Pure, and separate from
 * the composable, because the singular/plural boundary is the part worth a test.
 */
fun affectedCountLine(
    affectedCount: Int?,
    noun: String = "transaction",
    pluralNoun: String = noun + "s",
): String? {
    if (affectedCount == null) return null
    return "$affectedCount ${if (affectedCount == 1) noun else pluralNoun} affected"
}

/**
 * Confirmation before something that cannot be undone from the same screen — delete,
 * merge, reconcile, batch reversal.
 */
@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    affectedCount: Int?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirmLabel: String = "Confirm",
    dismissLabel: String = "Cancel",
    affectedNoun: String = "transaction",
    destructive: Boolean = false,
) {
    val colors = SakuTheme.colors
    val countLine = affectedCountLine(affectedCount, affectedNoun)

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = {
            Text(text = title, style = SakuTheme.typography.title, color = colors.ink)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(SakuTheme.spacing.sm)) {
                Text(text = body, style = SakuTheme.typography.body, color = colors.ink2)
                countLine?.let {
                    Text(
                        text = it,
                        style = SakuTheme.typography.bodyStrong,
                        color = colors.ink,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmLabel,
                    color = if (destructive) colors.expense else colors.accent,
                    style = SakuTheme.typography.bodyStrong,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = dismissLabel,
                    color = colors.ink2,
                    style = SakuTheme.typography.bodyStrong,
                )
            }
        },
        containerColor = colors.surface,
        titleContentColor = colors.ink,
        textContentColor = colors.ink2,
    )
}

@SakuPreview
@Composable
private fun ConfirmDialogPreview() {
    SakuPreviewSurface {
        ConfirmDialog(
            title = "Merge into Makan & Minum?",
            body = "Jajan & Hiburan will be archived and its transactions moved across.",
            affectedCount = 214,
            onConfirm = {},
            onDismiss = {},
            confirmLabel = "Merge",
        )
    }
}

@SakuPreview
@Composable
private fun ConfirmDialogNoCountPreview() {
    SakuPreviewSurface {
        ConfirmDialog(
            title = "Delete this transaction?",
            body = "The account balance will be recalculated.",
            affectedCount = null,
            onConfirm = {},
            onDismiss = {},
            confirmLabel = "Delete",
            destructive = true,
        )
    }
}
