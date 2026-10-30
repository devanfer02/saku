package com.dvnnfrr.saku.ui.shared

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.dvnnfrr.saku.ui.theme.SakuPreview
import com.dvnnfrr.saku.ui.theme.SakuPreviewSurface
import com.dvnnfrr.saku.ui.theme.SakuTheme

/**
 * The title row of a screen pushed from More: a back arrow, the title, and any actions.
 * Every one of them uses this, so each says where it is and has a way out besides
 * system back.
 */
@Composable
fun ScreenHeader(
    title: String,
    onBack: (() -> Unit)?,
    /** The back arrow's spoken label; copy arrives from the caller, as in every shared component. */
    backLabel: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val spacing = SakuTheme.spacing
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = spacing.touchTarget)
            .padding(horizontal = spacing.sm, vertical = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Null hides the arrow, for a host with its own way out.
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = backLabel,
                    tint = SakuTheme.colors.ink,
                )
            }
        }
        Text(
            text = title,
            style = SakuTheme.typography.title,
            color = SakuTheme.colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = spacing.sm)
                .semantics { heading() },
        )
        actions()
    }
}

@SakuPreview
@Composable
private fun ScreenHeaderPreview() {
    SakuPreviewSurface(padded = false) {
        ScreenHeader(title = "Back up everything", onBack = {}, backLabel = "Back")
    }
}
