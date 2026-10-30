package com.dvnnfrr.saku.ui.shared

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
