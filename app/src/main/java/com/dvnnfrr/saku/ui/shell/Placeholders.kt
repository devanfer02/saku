package com.dvnnfrr.saku.ui.shell

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dvnnfrr.saku.R
import com.dvnnfrr.saku.domain.ThemePreference
import com.dvnnfrr.saku.ui.theme.SakuPreview
import com.dvnnfrr.saku.ui.theme.SakuPreviewSurface
import com.dvnnfrr.saku.ui.theme.SakuTheme

// The tab placeholders are gone — Transactions, Stats and Accounts all mount their
// real screens now. What remains is the More menu, which was never a placeholder.

/**
 * The More menu is not a placeholder — it is the destination. Tracks C and F attach
 * their screens to these rows; the rows themselves are final.
 */
@Composable
fun MorePlaceholder(
    modifier: Modifier = Modifier,
    onSelect: (MoreEntry) -> Unit = {},
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(MoreEntry.entries.size) { index ->
            val entry = MoreEntry.entries[index]
            ListItem(
                headlineContent = { Text(stringResource(entry.labelRes)) },
                leadingContent = { Icon(entry.icon, contentDescription = null) },
                modifier = Modifier.clickable { onSelect(entry) },
            )
            if (index != MoreEntry.entries.lastIndex) HorizontalDivider()
        }
    }
}

/**
 * The More destination: the menu, plus the one setting that lives in it.
 *
 * Appearance is handled here rather than routed. It is a dialog over the menu, so
 * choosing a theme does not cost a back press to undo, and the user can see the menu
 * change behind it. Every other entry is a screen, and the caller decides where it
 * goes — the shell still owns no feature.
 */
@Composable
fun MoreScreen(
    appearance: ThemePreference,
    onAppearanceChange: (ThemePreference) -> Unit,
    modifier: Modifier = Modifier,
    onOpen: (MoreEntry) -> Unit = {},
) {
    var showAppearance by rememberSaveable { mutableStateOf(false) }

    MorePlaceholder(
        modifier = modifier,
        onSelect = { entry ->
            if (entry == MoreEntry.APPEARANCE) showAppearance = true else onOpen(entry)
        },
    )

    if (showAppearance) {
        AppearanceDialog(
            selected = appearance,
            onSelect = onAppearanceChange,
            onDismiss = { showAppearance = false },
        )
    }
}

/** Everything the More menu offers. Inert until each track wires its own route. */
enum class MoreEntry(@StringRes val labelRes: Int, val icon: ImageVector) {
    CATEGORIES(R.string.more_categories, Icons.Filled.Category),
    IMPORT_MONEY_MANAGER(R.string.more_import_money_manager, Icons.Filled.SwapHoriz),
    IMPORT_EXCEL(R.string.more_import_excel, Icons.Filled.FileUpload),
    EXPORT_EXCEL(R.string.more_export_excel, Icons.Filled.FileDownload),
    IMPORT_HISTORY(R.string.more_import_history, Icons.AutoMirrored.Filled.ListAlt),
    BACKUP(R.string.more_backup, Icons.Filled.Backup),
    RESTORE(R.string.more_restore, Icons.Filled.Restore),
    APPEARANCE(R.string.more_appearance, Icons.Filled.DarkMode),
}

@SakuPreview
@Composable
private fun MorePreview() {
    SakuPreviewSurface(padded = false) { MorePlaceholder() }
}

@SakuPreview
@Composable
private fun AppearanceRowPreview() {
    SakuPreviewSurface(padded = false) {
        MoreScreen(appearance = ThemePreference.DARK, onAppearanceChange = {})
    }
}
