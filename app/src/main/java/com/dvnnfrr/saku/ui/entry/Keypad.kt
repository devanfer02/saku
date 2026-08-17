package com.dvnnfrr.saku.ui.entry

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dvnnfrr.saku.R
import com.dvnnfrr.saku.ui.theme.SakuPreview
import com.dvnnfrr.saku.ui.theme.SakuPreviewSurface
import com.dvnnfrr.saku.ui.theme.SakuTheme

/**
 * The numeric keypad.
 *
 * On screen rather than the system IME because the reference layout puts it there,
 * and because it carries keys no IME has: `000`, and the `+`/`−` that let a user
 * total a receipt without leaving the sheet.
 *
 * Stateless. It reports keys; [AmountInput] decides what they mean.
 */
@Composable
fun Keypad(
    onDigit: (Int) -> Unit,
    onTripleZero: () -> Unit,
    onBackspace: () -> Unit,
    onOperator: (AmountOperator) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    saveEnabled: Boolean = true,
) {
    val spacing = SakuTheme.spacing

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SakuTheme.colors.surfaceAlt)
            .padding(spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        KeyRow {
            (1..3).forEach { Digit(it, onDigit) }
            Key(
                onClick = onBackspace,
                description = stringResource(R.string.keypad_backspace),
            ) {
                Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = null)
            }
        }
        KeyRow {
            (4..6).forEach { Digit(it, onDigit) }
            Key(onClick = { onOperator(AmountOperator.MINUS) }) { KeyLabel("−") }
        }
        KeyRow {
            (7..9).forEach { Digit(it, onDigit) }
            Key(onClick = { onOperator(AmountOperator.PLUS) }) { KeyLabel("+") }
        }
        KeyRow {
            Key(onClick = onTripleZero) { KeyLabel("000") }
            Digit(0, onDigit)
            Button(
                onClick = onSave,
                enabled = saveEnabled,
                shape = RoundedCornerShape(SakuTheme.spacing.corner),
                // onPrimary, not white. The accent is #1B5AD9 in light but #6C9BF0
                // in dark, and white on the dark accent is about 2.6:1 — unreadable.
                // The theme's Material bridge already picks the right ink per theme.
                colors = ButtonDefaults.buttonColors(
                    containerColor = SakuTheme.colors.accent,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier.weight(2f).height(KEY_HEIGHT),
            ) {
                Text(stringResource(R.string.action_save), style = SakuTheme.typography.bodyStrong)
            }
        }
    }
}

private val KEY_HEIGHT = 52.dp

@Composable
private fun KeyRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SakuTheme.spacing.xs),
        content = content,
    )
}

@Composable
private fun RowScope.Digit(digit: Int, onDigit: (Int) -> Unit) {
    Key(onClick = { onDigit(digit) }) { KeyLabel(digit.toString()) }
}

@Composable
private fun RowScope.Key(
    onClick: () -> Unit,
    description: String? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(SakuTheme.spacing.corner),
        color = SakuTheme.colors.surface,
        contentColor = SakuTheme.colors.ink,
        modifier = Modifier
            .weight(1f)
            .height(KEY_HEIGHT)
            .clip(RoundedCornerShape(SakuTheme.spacing.corner))
            .then(
                if (description == null) Modifier
                else Modifier.semantics { contentDescription = description },
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            content()
        }
    }
}

@Composable
private fun KeyLabel(text: String) {
    Text(text = text, style = SakuTheme.typography.title, color = SakuTheme.colors.ink)
}

@SakuPreview
@Composable
private fun KeypadPreview() {
    SakuPreviewSurface(padded = false) {
        Keypad(
            onDigit = {},
            onTripleZero = {},
            onBackspace = {},
            onOperator = {},
            onSave = {},
        )
    }
}
