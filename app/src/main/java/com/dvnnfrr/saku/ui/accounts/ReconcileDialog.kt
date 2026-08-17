package com.dvnnfrr.saku.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import com.dvnnfrr.saku.R
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.ui.shared.MoneyText
import com.dvnnfrr.saku.ui.theme.SakuPreview
import com.dvnnfrr.saku.ui.theme.SakuPreviewSurface
import com.dvnnfrr.saku.ui.theme.SakuTheme
import com.dvnnfrr.saku.ui.theme.formatIdr

/**
 * The user states what is really in the account; the app records the difference.
 *
 * The adjustment is shown before it is written, and it is written as an ordinary dated
 * transaction rather than as a correction to the balance. That is the whole point:
 * roughly half of a year of entries were reconstructed from memory days later
 * and small cash spend is systematically under-reported, so the size of these
 * adjustments is the only measurement of how much goes untracked. Overwriting the
 * balance would delete the measurement.
 */
@Composable
fun ReconcileDialog(
    account: AccountRowUi,
    onDismiss: () -> Unit,
    onConfirm: (Money) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SakuTheme.colors
    val spacing = SakuTheme.spacing
    var text by remember(account.id) { mutableStateOf(account.balance.rupiah.toString()) }
    val trueBalance = Money(text.toLongOrNull() ?: 0L)
    val adjustment = adjustmentFor(account.balance, trueBalance)

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        containerColor = colors.surface,
        titleContentColor = colors.ink,
        textContentColor = colors.ink2,
        title = {
            Text(
                text = stringResource(R.string.accounts_reconcile_title, account.name),
                style = SakuTheme.typography.title,
                color = colors.ink,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                LabelledFigure(
                    label = stringResource(R.string.accounts_reconcile_derived),
                    value = account.balance,
                )

                TrueBalanceField(value = text, onChange = { text = it })

                if (adjustment.isZero) {
                    Text(
                        text = stringResource(R.string.accounts_reconcile_matched),
                        style = SakuTheme.typography.caption,
                        color = colors.ink2,
                    )
                } else {
                    LabelledFigure(
                        label = stringResource(R.string.accounts_reconcile_adjustment),
                        value = adjustment,
                        signed = true,
                    )
                }
            }
        },
        confirmButton = {
            // Deliberately not "Reconcile": that is the button that opened this dialog
            // and it is still on the screen behind it, which leaves two identical
            // targets — one of them a no-op — for anyone driving by label.
            TextButton(onClick = { onConfirm(trueBalance) }) {
                Text(
                    text = stringResource(R.string.action_save),
                    color = colors.accent,
                    style = SakuTheme.typography.bodyStrong,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.action_cancel),
                    color = colors.ink2,
                    style = SakuTheme.typography.bodyStrong,
                )
            }
        },
    )
}

@Composable
private fun LabelledFigure(label: String, value: Money, signed: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = SakuTheme.typography.body, color = SakuTheme.colors.ink2)
        // type = null throughout: a balance and a correction to one have no direction,
        // and neither is spending.
        MoneyText(
            money = value,
            type = null,
            signed = signed,
            style = SakuTheme.typography.amountMedium,
            contentDescription = "$label ${formatIdr(value)}",
        )
    }
}

@Composable
private fun TrueBalanceField(value: String, onChange: (String) -> Unit) {
    val spacing = SakuTheme.spacing
    val label = stringResource(R.string.accounts_reconcile_true)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = SakuTheme.typography.body, color = SakuTheme.colors.ink2)
        BasicTextField(
            value = value,
            onValueChange = { raw ->
                // Digits and one leading minus: an overdrawn card reconciles to a
                // negative balance, and no amount in this app has a decimal component.
                val negative = raw.startsWith('-')
                val digits = raw.filter { it.isDigit() }
                onChange(if (negative) "-$digits" else digits)
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = TextStyle(
                color = SakuTheme.colors.ink,
                textAlign = TextAlign.End,
                fontSize = SakuTheme.typography.amountMedium.fontSize,
            ),
            modifier = Modifier
                .heightIn(min = spacing.touchTarget)
                .padding(start = spacing.md)
                .semantics { contentDescription = label },
        )
    }
}

@SakuPreview
@Composable
private fun ReconcileDialogPreview() {
    SakuPreviewSurface {
        ReconcileDialog(
            account = previewState().groups[1].rows[0],
            onDismiss = {},
            onConfirm = {},
        )
    }
}
