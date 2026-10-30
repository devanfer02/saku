package com.dvnnfrr.saku.ui.categories

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dvnnfrr.saku.R
import com.dvnnfrr.saku.domain.Category
import com.dvnnfrr.saku.domain.CategoryKind
import com.dvnnfrr.saku.domain.ColorSlot
import com.dvnnfrr.saku.ui.shared.ColorSwatch
import com.dvnnfrr.saku.ui.shared.EmptyState
import com.dvnnfrr.saku.ui.shared.ScreenHeader
import com.dvnnfrr.saku.ui.shared.SegmentedTabs
import com.dvnnfrr.saku.ui.theme.SakuPreview
import com.dvnnfrr.saku.ui.theme.SakuPreviewSurface
import com.dvnnfrr.saku.ui.theme.SakuTheme

/**
 * Managing the list the entry sheet files against: name, colour, order, archive, merge.
 *
 * Categories only. Subcategories were dropped on 30 Oct 2026 in favour of search, so
 * their stored rows are kept but no longer listed here.
 */
@Composable
fun CategoriesScreen(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    viewModel: CategoryManagerViewModel = viewModel(factory = CategoryManagerViewModel.Factory),
) {
    val state by viewModel.state.collectAsState()
    CategoriesScreen(
        state = state,
        actions = rememberCategoryListActions(viewModel),
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
fun CategoriesScreen(
    state: CategoryManagerUiState,
    actions: CategoryListActions,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    val spacing = SakuTheme.spacing
    val listState = rememberLazyListState()
    val drag = rememberCategoryDrag(listState, actions)

    Column(modifier = modifier.fillMaxSize()) {
        Header(onBack = onBack, onCreate = actions.onCreateCategory)

        SegmentedTabs(
            options = listOf(
                stringResource(R.string.categories_tab_expense),
                stringResource(R.string.categories_tab_income),
            ),
            selectedIndex = state.kind.ordinal,
            onSelect = { actions.onSelectKind(CategoryKind.entries[it]) },
            modifier = Modifier.padding(horizontal = spacing.screen, vertical = spacing.sm),
        )

        when {
            state.loading -> Unit

            state.isEmpty -> EmptyState(
                icon = Icons.Outlined.Category,
                message = stringResource(R.string.categories_empty),
                action = {
                    TextButton(onClick = actions.onCreateCategory) {
                        Text(
                            text = stringResource(R.string.categories_new_category),
                            color = SakuTheme.colors.accent,
                            style = SakuTheme.typography.bodyStrong,
                        )
                    }
                },
            )

            else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                categoryRows(
                    rows = state.active,
                    state = state,
                    actions = actions,
                    drag = drag,
                    reorderable = state.active.size > 1,
                )

                if (state.archived.isNotEmpty()) {
                    item(key = "archived-toggle") {
                        ArchivedToggle(
                            count = state.archived.size,
                            expanded = state.showArchived,
                            onToggle = { actions.onSetShowArchived(!state.showArchived) },
                        )
                    }
                }

                if (state.showArchived) {
                    categoryRows(
                        rows = state.archived,
                        state = state,
                        actions = actions,
                        drag = drag,
                        reorderable = false,
                    )
                }
            }
        }
    }

    CategoryDialogHost(dialog = state.dialog, actions = actions)
}

private fun LazyListScope.categoryRows(
    rows: List<CategoryRow>,
    state: CategoryManagerUiState,
    actions: CategoryListActions,
    drag: CategoryDragState,
    reorderable: Boolean,
) {
    rows.forEachIndexed { index, row ->
        // Keyed by the raw id. The drag reads the key back off the layout to find the row
        // it is over, and anything that is not a draggable category is keyed by a String.
        item(key = row.id) {
            CategoryRowItem(
                row = row,
                menuOpen = state.menuForCategoryId == row.id,
                canMoveUp = reorderable && index > 0,
                canMoveDown = reorderable && index < rows.lastIndex,
                // Nothing to merge an archived category into, and nowhere sensible for
                // it to land: it is already out of the pickers.
                mergeable = !row.archived && state.mergeTargetsFor(row.id).isNotEmpty(),
                dragging = drag.draggingId == row.id,
                dragOffset = if (drag.draggingId == row.id) drag.offset else 0f,
                reorderable = reorderable,
                drag = drag,
                actions = actions,
            )
        }

        item(key = "divider-${row.id}") {
            HorizontalDivider(color = SakuTheme.colors.line)
        }
    }
}

@Composable
private fun Header(onBack: (() -> Unit)?, onCreate: () -> Unit) {
    ScreenHeader(title = stringResource(R.string.categories_title), onBack = onBack, backLabel = stringResource(R.string.screen_back)) {
        IconButton(onClick = onCreate) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(R.string.categories_new_category),
                tint = SakuTheme.colors.accent,
            )
        }
    }
}

@Composable
private fun CategoryRowItem(
    row: CategoryRow,
    menuOpen: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    mergeable: Boolean,
    dragging: Boolean,
    dragOffset: Float,
    reorderable: Boolean,
    drag: CategoryDragState,
    actions: CategoryListActions,
) {
    val colors = SakuTheme.colors
    val spacing = SakuTheme.spacing
    val countLabel = pluralStringResource(
        R.plurals.categories_transaction_count,
        row.transactionCount,
        row.transactionCount,
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer { translationY = dragOffset }
            .clickable(onClick = { actions.onOpenMenu(row.id) })
            .defaultMinSize(minHeight = spacing.rowMinHeight)
            .padding(end = spacing.sm)
            .testTag("category-${row.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        if (reorderable) {
            DragHandle(row = row, canMoveUp = canMoveUp, canMoveDown = canMoveDown, drag = drag, actions = actions)
        } else {
            Box(modifier = Modifier.width(spacing.screen))
        }

        ColorSwatch(row.colorSlot)

        Column(modifier = Modifier.weight(1f).padding(vertical = spacing.sm)) {
            Text(
                text = row.name,
                style = SakuTheme.typography.body,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = countLabel,
                style = SakuTheme.typography.caption,
                color = colors.ink2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Box {
            IconButton(onClick = { actions.onOpenMenu(row.id) }) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(
                        R.string.categories_actions_for,
                        row.name,
                    ),
                    tint = colors.ink2,
                )
            }
            CategoryMenu(
                row = row,
                open = menuOpen,
                canMoveUp = canMoveUp,
                canMoveDown = canMoveDown,
                mergeable = mergeable,
                actions = actions,
            )
        }
    }
}

/**
 * The reorder control.
 *
 * Long-press drag is the fast path, and the same two moves are exposed as accessibility
 * actions and as menu items. A drag handle on its own is unreachable by keyboard and
 * meaningless to a screen reader, which would make sort order an ability-gated feature.
 */
@Composable
private fun DragHandle(
    row: CategoryRow,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    drag: CategoryDragState,
    actions: CategoryListActions,
) {
    val handleLabel = stringResource(R.string.categories_reorder, row.name)
    val upLabel = stringResource(R.string.categories_action_move_up)
    val downLabel = stringResource(R.string.categories_action_move_down)

    Box(
        modifier = Modifier
            .size(48.dp)
            .pointerInput(row.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { drag.start(row.id) },
                    onDrag = { change, amount ->
                        change.consume()
                        drag.drag(amount.y)
                    },
                    onDragEnd = { drag.end() },
                    onDragCancel = { drag.end() },
                )
            }
            .semantics {
                contentDescription = handleLabel
                customActions = buildList {
                    if (canMoveUp) {
                        add(CustomAccessibilityAction(upLabel) { actions.onMoveCategory(row.id, -1); true })
                    }
                    if (canMoveDown) {
                        add(CustomAccessibilityAction(downLabel) { actions.onMoveCategory(row.id, 1); true })
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.DragHandle,
            contentDescription = null,
            tint = SakuTheme.colors.ink3,
        )
    }
}

@Composable
private fun CategoryMenu(
    row: CategoryRow,
    open: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    mergeable: Boolean,
    actions: CategoryListActions,
) {
    DropdownMenu(
        expanded = open,
        onDismissRequest = actions.onCloseMenus,
        containerColor = SakuTheme.colors.surface,
    ) {
        MenuItem(R.string.categories_action_rename) { actions.onRenameCategory(row.id) }
        MenuItem(R.string.categories_action_recolour) { actions.onRecolorCategory(row.id) }
        if (canMoveUp) MenuItem(R.string.categories_action_move_up) { actions.onMoveCategory(row.id, -1) }
        if (canMoveDown) MenuItem(R.string.categories_action_move_down) { actions.onMoveCategory(row.id, 1) }
        if (mergeable) MenuItem(R.string.categories_action_merge) { actions.onMergeCategory(row.id) }

        if (row.archived) {
            MenuItem(R.string.categories_action_unarchive) { actions.onArchiveCategory(row.id, false) }
        } else {
            MenuItem(R.string.categories_action_archive) { actions.onArchiveCategory(row.id, true) }
        }

        // Delete appears only where nothing refers to the category. Everywhere else archive is the offer, so history cannot be thrown away by
        // reaching for the wrong item in a menu.
        if (row.deletable) {
            MenuItem(R.string.action_delete, destructive = true) {
                actions.onDeleteCategory(row.id)
            }
        }
    }
}

@Composable
private fun ArchivedToggle(count: Int, expanded: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .defaultMinSize(minHeight = SakuTheme.spacing.touchTarget)
            .padding(horizontal = SakuTheme.spacing.screen),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (expanded) {
                stringResource(R.string.categories_hide_archived)
            } else {
                stringResource(R.string.categories_show_archived, count)
            },
            style = SakuTheme.typography.bodyStrong,
            color = SakuTheme.colors.accent,
        )
    }
}

@Composable
private fun MenuItem(
    @StringRes labelRes: Int,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Text(
                text = stringResource(labelRes),
                style = SakuTheme.typography.body,
                color = if (destructive) SakuTheme.colors.expense else SakuTheme.colors.ink,
            )
        },
        onClick = onClick,
    )
}

/**
 * A drag in progress.
 *
 * Nothing is written while the finger is down. The order is rearranged in memory and
 * saved once on the drop, so a fast drag across a long list is one write rather than one
 * per row it passes.
 */
@Stable
class CategoryDragState(
    private val listState: LazyListState,
    private val actions: CategoryListActions,
) {
    var draggingId by mutableStateOf<Long?>(null)
        private set

    var offset by mutableFloatStateOf(0f)
        private set

    fun start(id: Long) {
        draggingId = id
        offset = 0f
        actions.onStartReorder()
    }

    fun drag(delta: Float) {
        val id = draggingId ?: return
        offset += delta
        val items = listState.layoutInfo.visibleItemsInfo
        val held = items.firstOrNull { it.key == id } ?: return
        val centre = held.offset + held.size / 2f + offset
        val over = items.firstOrNull { candidate ->
            candidate.key is Long &&
                candidate.key != id &&
                centre >= candidate.offset &&
                centre <= candidate.offset + candidate.size
        } ?: return
        actions.onDragOver(id, over.key as Long)
        // The two rows have traded places. Without discounting the distance that trade
        // already moved the held row, the offset keeps its old value and the row jumps
        // a second time on the next frame.
        offset -= (over.offset - held.offset)
    }

    fun end() {
        if (draggingId == null) return
        draggingId = null
        offset = 0f
        actions.onDropReorder()
    }
}

@Composable
private fun rememberCategoryDrag(
    listState: LazyListState,
    actions: CategoryListActions,
): CategoryDragState = remember(listState, actions) { CategoryDragState(listState, actions) }

/** One holder rather than twenty parameters, so the list stays previewable without a database. */
@Immutable
data class CategoryListActions(
    val onSelectKind: (CategoryKind) -> Unit,
    val onSetShowArchived: (Boolean) -> Unit,
    val onOpenMenu: (Long) -> Unit,
    val onCloseMenus: () -> Unit,
    val onCreateCategory: () -> Unit,
    val onRenameCategory: (Long) -> Unit,
    val onRecolorCategory: (Long) -> Unit,
    val onMergeCategory: (Long) -> Unit,
    val onArchiveCategory: (Long, Boolean) -> Unit,
    val onDeleteCategory: (Long) -> Unit,
    val onMoveCategory: (Long, Int) -> Unit,
    val onStartReorder: () -> Unit,
    val onDragOver: (Long, Long) -> Unit,
    val onDropReorder: () -> Unit,
    val onSubmitName: (String) -> Unit,
    val onRecolor: (ColorSlot) -> Unit,
    val onConfirmDelete: () -> Unit,
    val onChooseMergeTarget: (Long) -> Unit,
    val onConfirmMerge: () -> Unit,
    val onDismissDialog: () -> Unit,
)

@Composable
private fun rememberCategoryListActions(
    viewModel: CategoryManagerViewModel,
): CategoryListActions = remember(viewModel) {
    CategoryListActions(
        onSelectKind = viewModel::selectKind,
        onSetShowArchived = viewModel::setShowArchived,
        onOpenMenu = viewModel::openMenu,
        onCloseMenus = viewModel::closeMenus,
        onCreateCategory = viewModel::promptCreateCategory,
        onRenameCategory = viewModel::promptRenameCategory,
        onRecolorCategory = viewModel::promptRecolor,
        onMergeCategory = viewModel::promptMerge,
        onArchiveCategory = viewModel::setCategoryArchived,
        onDeleteCategory = viewModel::promptDeleteCategory,
        onMoveCategory = viewModel::moveCategory,
        onStartReorder = viewModel::startReorder,
        onDragOver = viewModel::moveCategoryOver,
        onDropReorder = viewModel::commitReorder,
        onSubmitName = viewModel::submitName,
        onRecolor = viewModel::recolor,
        onConfirmDelete = viewModel::confirmDelete,
        onChooseMergeTarget = viewModel::chooseMergeTarget,
        onConfirmMerge = viewModel::confirmMerge,
        onDismissDialog = viewModel::dismissDialog,
    )
}

private fun previewRow(
    id: Long,
    name: String,
    slot: Int,
    order: Int,
    transactions: Int,
) = CategoryRow(
    category = Category(
        id = id,
        name = name,
        kind = CategoryKind.EXPENSE,
        colorSlot = ColorSlot(slot),
        sortOrder = order,
        archived = false,
    ),
    transactionCount = transactions,
)

private fun noActions() = CategoryListActions(
    onSelectKind = {},
    onSetShowArchived = {},
    onOpenMenu = {},
    onCloseMenus = {},
    onCreateCategory = {},
    onRenameCategory = {},
    onRecolorCategory = {},
    onMergeCategory = {},
    onArchiveCategory = { _, _ -> },
    onDeleteCategory = {},
    onMoveCategory = { _, _ -> },
    onStartReorder = {},
    onDragOver = { _, _ -> },
    onDropReorder = {},
    onSubmitName = {},
    onRecolor = {},
    onConfirmDelete = {},
    onChooseMergeTarget = {},
    onConfirmMerge = {},
    onDismissDialog = {},
)

@SakuPreview
@Composable
private fun CategoriesScreenPreview() {
    SakuPreviewSurface(padded = false) {
        CategoriesScreen(
            state = CategoryManagerUiState(
                active = listOf(
                    previewRow(1, "Pemberian", 0, 0, 443),
                    previewRow(2, "Makan & Minum", 1, 1, 512),
                    previewRow(3, "Kebutuhan Kos", 2, 2, 39),
                ),
                loading = false,
            ),
            actions = noActions(),
            onBack = {},
        )
    }
}

@SakuPreview
@Composable
private fun CategoriesScreenEmptyPreview() {
    SakuPreviewSurface(padded = false) {
        CategoriesScreen(
            state = CategoryManagerUiState(kind = CategoryKind.INCOME, loading = false),
            actions = noActions(),
            onBack = {},
        )
    }
}
