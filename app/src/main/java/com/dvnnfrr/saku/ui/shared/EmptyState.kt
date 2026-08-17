package com.dvnnfrr.saku.ui.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dvnnfrr.saku.ui.theme.SakuPreview
import com.dvnnfrr.saku.ui.theme.SakuPreviewSurface
import com.dvnnfrr.saku.ui.theme.SakuTheme

/**
 * What a screen shows when it has nothing to show. The action slot is optional because
 * some empty states have an obvious next step — import a file — and some, such as a past
 * month with no spending, are simply a fact.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = SakuTheme.colors
    val spacing = SakuTheme.spacing

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.xl, vertical = spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.ink3,
            modifier = Modifier.size(40.dp),
        )
        Text(
            text = message,
            style = SakuTheme.typography.body,
            color = colors.ink2,
            textAlign = TextAlign.Center,
        )
        action?.invoke()
    }
}

@SakuPreview
@Composable
private fun EmptyStatePreview() {
    SakuPreviewSurface(padded = false) {
        Column(modifier = Modifier.width(360.dp)) {
            EmptyState(
                icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                message = "No spending recorded in August 2026.",
            )
            EmptyState(
                icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                message = "Nothing imported yet.",
                action = {
                    TextButton(onClick = {}) {
                        Text(
                            text = "Import from Money Manager",
                            color = SakuTheme.colors.accent,
                            style = SakuTheme.typography.bodyStrong,
                        )
                    }
                },
            )
        }
    }
}
