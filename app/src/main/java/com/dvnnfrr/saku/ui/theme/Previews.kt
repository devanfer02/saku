package com.dvnnfrr.saku.ui.theme

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

/**
 * Renders a component in both themes. Every shared component carries this so that a
 * token missing from one set shows up in the preview pane rather than on a device.
 */
@Preview(name = "Light", group = "themes", showBackground = true)
@Preview(
    name = "Dark",
    group = "themes",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
annotation class SakuPreview

/** Preview scaffold: applies the theme and paints the page background behind the sample. */
@Composable
fun SakuPreviewSurface(
    modifier: Modifier = Modifier,
    padded: Boolean = true,
    content: @Composable () -> Unit,
) {
    SakuTheme {
        Box(
            modifier = modifier
                .background(SakuTheme.colors.background)
                .then(if (padded) Modifier.padding(SakuTheme.spacing.lg) else Modifier),
        ) {
            content()
        }
    }
}
