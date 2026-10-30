package com.dvnnfrr.saku.ui.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dvnnfrr.saku.domain.Account
import com.dvnnfrr.saku.domain.AccountType
import com.dvnnfrr.saku.domain.Category
import com.dvnnfrr.saku.domain.CategoryKind
import com.dvnnfrr.saku.domain.ColorSlot
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.ui.theme.SakuPreview
import com.dvnnfrr.saku.ui.theme.SakuPreviewSurface
import com.dvnnfrr.saku.ui.theme.SakuTheme
import kotlinx.datetime.Instant

/*
 * The pickers take their list and their callbacks as parameters and reach nothing.
 * Three screens use them — the entry sheet, import mapping and stats filters — and each
 * sources its list differently, so a picker that fetched its own would be wrong for two
 * of the three.
 *
 * Each is list content rather than a dialog, so a caller can drop one into a sheet it is
 * already showing instead of stacking a second one on top.
 */

/**
 * Categories to choose from, ending in a create action.
 *
 * Archived records are dropped here as well as by the caller's query; a picker is the
 * one place an archived category must never reappear, so it does not rely on the caller
 * having remembered.
 */
@Composable
fun CategoryPicker(
    categories: List<Category>,
    onSelect: (Category) -> Unit,
    onCreateNew: (() -> Unit)?,
    modifier: Modifier = Modifier,
    selectedId: Long? = null,
    createLabel: String = "New category",
) {
    val visible = categories.filterNot { it.archived }
    PickerList(
        modifier = modifier,
        itemCount = visible.size,
        onCreateNew = onCreateNew,
        createLabel = createLabel,
    ) {
        items(visible, key = { it.id }) { category ->
            PickerRow(
                label = category.name,
                slot = category.colorSlot,
                selected = category.id == selectedId,
                onClick = { onSelect(category) },
            )
        }
    }
}

/** Accounts to choose from, each with its pinned colour swatch. */
@Composable
fun AccountPicker(
    accounts: List<Account>,
    onSelect: (Account) -> Unit,
    onCreateNew: (() -> Unit)?,
    modifier: Modifier = Modifier,
    selectedId: Long? = null,
    createLabel: String = "New account",
) {
    val visible = accounts.filterNot { it.archived }
    PickerList(
        modifier = modifier,
        itemCount = visible.size,
        onCreateNew = onCreateNew,
        createLabel = createLabel,
    ) {
        items(visible, key = { it.id }) { account ->
            PickerRow(
                label = account.name,
                slot = account.colorSlot,
                selected = account.id == selectedId,
                onClick = { onSelect(account) },
            )
        }
    }
}

/**
 * The same choices as a three-column grid, for the entry sheet, where it takes the
 * keypad's place: twenty-odd categories fit on screen in a handful of rows instead of a
 * list scrolled one name at a time. Cells are split by `lineStrong` gaps so
 * each reads as its own target.
 */
@Composable
fun CategoryPickerGrid(
    categories: List<Category>,
    onSelect: (Category) -> Unit,
    onCreateNew: (() -> Unit)?,
    modifier: Modifier = Modifier,
    selectedId: Long? = null,
    createLabel: String = "New category",
) {
    val cells = categories.filterNot { it.archived }.map { category ->
        GridCell(
            key = category.id,
            label = category.name,
            selected = category.id == selectedId,
            onClick = { onSelect(category) },
        )
    }
    PickerGrid(cells = cells, modifier = modifier, onCreateNew = onCreateNew, createLabel = createLabel)
}

/** Accounts as a grid; no create action, since accounts are made on the Accounts screen. */
@Composable
fun AccountPickerGrid(
    accounts: List<Account>,
    onSelect: (Account) -> Unit,
    modifier: Modifier = Modifier,
    selectedId: Long? = null,
) {
    val cells = accounts.filterNot { it.archived }.map { account ->
        GridCell(
            key = account.id,
            label = account.name,
            selected = account.id == selectedId,
            onClick = { onSelect(account) },
        )
    }
    PickerGrid(cells = cells, modifier = modifier, onCreateNew = null, createLabel = "")
}

private class GridCell(val key: Long, val label: String, val selected: Boolean, val onClick: () -> Unit)

private const val GRID_COLUMNS = 3

@Composable
private fun PickerGrid(
    cells: List<GridCell>,
    modifier: Modifier,
    onCreateNew: (() -> Unit)?,
    createLabel: String,
) {
    val colors = SakuTheme.colors
    val gap = 1.dp
    val used = cells.size + if (onCreateNew != null) 1 else 0
    // Blank cells fill the last row, so the line colour behind the grid never shows
    // through as a solid block where a row runs short.
    val blanks = (GRID_COLUMNS - used % GRID_COLUMNS) % GRID_COLUMNS

    LazyVerticalGrid(
        columns = GridCells.Fixed(GRID_COLUMNS),
        modifier = modifier.fillMaxWidth().background(colors.lineStrong),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        gridItems(cells, key = { it.key }) { cell ->
            GridCellBox(
                selected = cell.selected,
                onClick = cell.onClick,
                modifier = Modifier.semantics {
                    role = Role.RadioButton
                    selected = cell.selected
                },
            ) {
                Text(
                    text = cell.label,
                    style = if (cell.selected) SakuTheme.typography.bodyStrong else SakuTheme.typography.body,
                    color = if (cell.selected) colors.accent else colors.ink,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onCreateNew != null) {
            item(key = "create") {
                GridCellBox(selected = false, onClick = onCreateNew) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(SakuTheme.spacing.xs),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = createLabel,
                            style = SakuTheme.typography.bodyStrong,
                            color = colors.accent,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        items(blanks) { Box(modifier = Modifier.background(colors.surface).heightIn(min = GRID_CELL_HEIGHT)) }
    }
}

private val GRID_CELL_HEIGHT = 56.dp

@Composable
private fun GridCellBox(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = SakuTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(if (selected) colors.accentWash else colors.surface)
            .clickable(onClick = onClick)
            .heightIn(min = GRID_CELL_HEIGHT)
            .padding(horizontal = SakuTheme.spacing.xs, vertical = SakuTheme.spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun PickerList(
    modifier: Modifier,
    itemCount: Int,
    onCreateNew: (() -> Unit)?,
    createLabel: String,
    rows: LazyListScope.() -> Unit,
) {
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        rows()
        // Null means this picker has nothing to create. Offering the row anyway gave
        // the account picker a "New account" action wired to subcategory creation.
        if (onCreateNew != null) {
            if (itemCount > 0) {
                item { HorizontalDivider(color = SakuTheme.colors.line) }
            }
            item { CreateNewRow(label = createLabel, onClick = onCreateNew) }
        }
    }
}

@Composable
private fun PickerRow(
    label: String,
    slot: ColorSlot?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = SakuTheme.colors
    val spacing = SakuTheme.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics {
                role = Role.RadioButton
                this.selected = selected
            }
            .defaultMinSize(minHeight = spacing.touchTarget)
            .padding(horizontal = spacing.screen, vertical = spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        if (slot != null) {
            ColorSwatch(slot)
        } else {
            Box(modifier = Modifier.size(spacing.swatch))
        }
        Text(
            text = label,
            style = SakuTheme.typography.body,
            color = colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun CreateNewRow(label: String, onClick: () -> Unit) {
    val colors = SakuTheme.colors
    val spacing = SakuTheme.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = spacing.touchTarget)
            .padding(horizontal = spacing.screen, vertical = spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Icon(
            imageVector = Icons.Outlined.Add,
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            style = SakuTheme.typography.bodyStrong,
            color = colors.accent,
        )
    }
}

private fun previewCategories() = listOf(
    Category(1, "Pemberian", CategoryKind.EXPENSE, ColorSlot(0), 0, archived = false),
    Category(2, "Makan & Minum", CategoryKind.EXPENSE, ColorSlot(1), 1, archived = false),
    Category(3, "Kebutuhan Kos", CategoryKind.EXPENSE, ColorSlot(2), 2, archived = false),
    Category(4, "Lama", CategoryKind.EXPENSE, ColorSlot(3), 3, archived = true),
)

private fun previewAccounts() = listOf(
    Account(
        id = 1,
        name = "BCA",
        type = AccountType.BANK,
        colorSlot = ColorSlot(0),
        sortOrder = 0,
        archived = false,
        openingBalance = Money(1_000_000),
        openingAsOf = Instant.fromEpochSeconds(0),
        lastReconciled = null,
        balance = Money(4_250_000),
    ),
    Account(
        id = 2,
        name = "Tunai",
        type = AccountType.CASH,
        colorSlot = ColorSlot(4),
        sortOrder = 1,
        archived = false,
        openingBalance = Money.ZERO,
        openingAsOf = Instant.fromEpochSeconds(0),
        lastReconciled = null,
        balance = Money(320_000),
    ),
)

@SakuPreview
@Composable
private fun CategoryPickerPreview() {
    SakuPreviewSurface(padded = false) {
        Column(modifier = Modifier.width(360.dp).heightIn(max = 320.dp)) {
            CategoryPicker(
                categories = previewCategories(),
                onSelect = {},
                onCreateNew = {},
                selectedId = 2,
            )
        }
    }
}

@SakuPreview
@Composable
private fun AccountPickerPreview() {
    SakuPreviewSurface(padded = false) {
        Column(modifier = Modifier.width(360.dp).heightIn(max = 320.dp)) {
            AccountPicker(
                accounts = previewAccounts(),
                onSelect = {},
                onCreateNew = {},
                selectedId = 1,
            )
        }
    }
}

@SakuPreview
@Composable
private fun EmptyCategoryPickerPreview() {
    SakuPreviewSurface(padded = false) {
        Column(modifier = Modifier.width(360.dp)) {
            CategoryPicker(categories = emptyList(), onSelect = {}, onCreateNew = {})
        }
    }
}

@SakuPreview
@Composable
private fun CategoryPickerGridPreview() {
    SakuPreviewSurface(padded = false) {
        Column(modifier = Modifier.width(360.dp)) {
            CategoryPickerGrid(
                categories = previewCategories(),
                onSelect = {},
                onCreateNew = {},
                selectedId = 2,
            )
        }
    }
}
