package com.dvnnfrr.saku.ui.shell

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.dvnnfrr.saku.R
import com.dvnnfrr.saku.domain.ThemePreference
import com.dvnnfrr.saku.ui.theme.SakuPreview
import com.dvnnfrr.saku.ui.theme.SakuPreviewSurface
import com.dvnnfrr.saku.ui.theme.SakuTheme

/**
 * The More menu, grouped so eight rows read as three decisions: how the ledger is
 * organised, what happens to the data, and how the app looks. Each row says in one line
 * what it does, because "Import Excel" beside "Import from Money Manager" does not.
 */
@Composable
fun MoreMenu(
    modifier: Modifier = Modifier,
    onSelect: (MoreEntry) -> Unit = {},
) {
    val colors = SakuTheme.colors
    val groups = MoreEntry.entries.groupBy { it.group }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        MoreGroup.entries.forEach { group ->
            val entries = groups[group].orEmpty()
            item(key = "group-${group.name}") { GroupHeading(stringResource(group.labelRes)) }
            entries.forEachIndexed { index, entry ->
                item(key = entry.name) {
                    ListItem(
                        headlineContent = { Text(stringResource(entry.labelRes), color = colors.ink) },
                        supportingContent = {
                            Text(
                                text = stringResource(entry.subtitleRes),
                                style = SakuTheme.typography.caption,
                                color = colors.ink2,
                            )
                        },
                        leadingContent = { Icon(entry.icon, contentDescription = null, tint = colors.ink2) },
                        modifier = Modifier.clickable { onSelect(entry) },
                    )
                    if (index != entries.lastIndex) HorizontalDivider(color = colors.line)
                }
            }
        }
    }
}

@Composable
private fun GroupHeading(text: String) {
    Text(
        text = text,
        style = SakuTheme.typography.label,
        color = SakuTheme.colors.ink2,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = SakuTheme.spacing.screen,
                end = SakuTheme.spacing.screen,
                top = SakuTheme.spacing.xl,
                bottom = SakuTheme.spacing.xs,
            )
            .semantics { heading() },
    )
}

/**
 * The More destination: the menu, plus the one setting that lives in it.
 *
 * Appearance is handled here rather than routed. It is a dialog over the menu, so
 * choosing a theme does not cost a back press to undo, and the user can see the menu
 * change behind it. Every other entry is a screen, and the caller decides where it
 * goes; the shell still owns no feature.
 */
@Composable
fun MoreScreen(
    appearance: ThemePreference,
    onAppearanceChange: (ThemePreference) -> Unit,
    modifier: Modifier = Modifier,
    onOpen: (MoreEntry) -> Unit = {},
) {
    var showAppearance by rememberSaveable { mutableStateOf(false) }

    MoreMenu(
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

/** The More menu's headings, in the order they are listed. */
enum class MoreGroup(@StringRes val labelRes: Int) {
    ORGANISE(R.string.more_group_organise),
    YOUR_DATA(R.string.more_group_data),
    APP(R.string.more_group_app),
}

/** Everything the More menu offers, in listing order within each group. */
enum class MoreEntry(
    @StringRes val labelRes: Int,
    @StringRes val subtitleRes: Int,
    val icon: ImageVector,
    val group: MoreGroup,
) {
    CATEGORIES(R.string.more_categories, R.string.more_categories_subtitle, Icons.Filled.Category, MoreGroup.ORGANISE),
    BACKUP(R.string.more_backup, R.string.more_backup_subtitle, Icons.Filled.Backup, MoreGroup.YOUR_DATA),
    RESTORE(R.string.more_restore, R.string.more_restore_subtitle, Icons.Filled.Restore, MoreGroup.YOUR_DATA),
    IMPORT_MONEY_MANAGER(
        R.string.more_import_money_manager,
        R.string.more_import_money_manager_subtitle,
        Icons.Filled.SwapHoriz,
        MoreGroup.YOUR_DATA,
    ),
    IMPORT_EXCEL(R.string.more_import_excel, R.string.more_import_excel_subtitle, Icons.Filled.FileUpload, MoreGroup.YOUR_DATA),
    EXPORT_EXCEL(R.string.more_export_excel, R.string.more_export_excel_subtitle, Icons.Filled.FileDownload, MoreGroup.YOUR_DATA),
    IMPORT_HISTORY(
        R.string.more_import_history,
        R.string.more_import_history_subtitle,
        Icons.AutoMirrored.Filled.ListAlt,
        MoreGroup.YOUR_DATA,
    ),
    APPEARANCE(R.string.more_appearance, R.string.more_appearance_subtitle, Icons.Filled.DarkMode, MoreGroup.APP),
}

@SakuPreview
@Composable
private fun MorePreview() {
    SakuPreviewSurface(padded = false) { MoreMenu() }
}

@SakuPreview
@Composable
private fun AppearanceRowPreview() {
    SakuPreviewSurface(padded = false) {
        MoreScreen(appearance = ThemePreference.DARK, onAppearanceChange = {})
    }
}
