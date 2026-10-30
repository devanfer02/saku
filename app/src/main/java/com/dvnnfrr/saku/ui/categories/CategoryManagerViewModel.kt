package com.dvnnfrr.saku.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dvnnfrr.saku.data.repository.CategoryRepository
import com.dvnnfrr.saku.data.repository.DuplicateNameException
import com.dvnnfrr.saku.domain.CategoryKind
import com.dvnnfrr.saku.domain.ColorSlot
import com.dvnnfrr.saku.sakuViewModelFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The category screen's state and every write it can make.
 *
 * Nothing here deletes in bulk. Archiving sets a flag, merging moves rows with a scoped
 * UPDATE inside one database transaction, and delete is offered only where the record and
 * everything under it is referenced by nothing at all.
 */
class CategoryManagerViewModel(
    private val repository: CategoryRepository,
) : ViewModel() {

    private data class Local(
        val kind: CategoryKind = CategoryKind.EXPENSE,
        val showArchived: Boolean = false,
        val menuForCategoryId: Long? = null,
        val dialog: CategoryDialog? = null,
        /** The order a drag is painting, before it is written down. */
        val pendingOrder: List<Long>? = null,
    )

    private val local = MutableStateFlow(Local())

    /**
     * Bumped after a write the category table cannot report on its own.
     *
     * The per-row transaction counts come from a grouped one-shot read, not from a
     * Flow. A merge moves transactions between categories without changing either table's
     * shape, so without this the counts on screen would still describe the old split.
     */
    private val recount = MutableStateFlow(0)

    private val rows: Flow<List<CategoryRow>> =
        combine(repository.observeAll(), recount) { categories, _ -> categories }
            .map { categories ->
                buildCategoryRows(
                    categories = categories,
                    categoryCounts = repository.transactionCountsByCategory(),
                )
            }
            .onEach { emitted ->
                // Drop the optimistic order once the database agrees with it. Clearing it
                // when the write returns instead made the list flick back to the old order
                // for the frame between the write landing and the Flow re-emitting.
                val pending = local.value.pendingOrder ?: return@onEach
                val settled = emitted
                    .filter { it.kind == local.value.kind && !it.archived }
                    .map { it.id }
                if (settled == pending) local.update { it.copy(pendingOrder = null) }
            }

    val state: StateFlow<CategoryManagerUiState> =
        combine(local, rows) { current, all ->
            val ofKind = all.filter { it.kind == current.kind }
            CategoryManagerUiState(
                kind = current.kind,
                active = ofKind.filterNot { it.archived }.applyOrder(current.pendingOrder),
                archived = ofKind.filter { it.archived },
                showArchived = current.showArchived,
                menuForCategoryId = current.menuForCategoryId,
                dialog = current.dialog,
                loading = false,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CategoryManagerUiState(),
        )

    fun selectKind(kind: CategoryKind) = local.update {
        // The pending order belongs to the tab it was dragged on; carrying it across
        // would reorder the other tab by ids it does not contain.
        it.copy(kind = kind, pendingOrder = null, dialog = null)
    }

    fun setShowArchived(show: Boolean) = local.update { it.copy(showArchived = show) }

    fun openMenu(categoryId: Long) = local.update { it.copy(menuForCategoryId = categoryId) }

    fun closeMenus() = local.update { it.copy(menuForCategoryId = null) }

    fun dismissDialog() = local.update { it.copy(dialog = null) }

    private fun show(dialog: CategoryDialog) = local.update {
        it.copy(dialog = dialog, menuForCategoryId = null)
    }

    /*
     * The prompts that only need a tab or a dialog read `local`, which updates on the
     * calling frame. The ones that need a row read `state`, which is a frame behind
     * because it is folded from the database. Reading a just-set dialog back out of
     * `state` returned the previous value and the submit silently did nothing.
     */

    fun promptCreateCategory() = show(CreateCategoryPrompt(local.value.kind))

    fun promptRenameCategory(categoryId: Long) {
        val row = state.value.rowFor(categoryId) ?: return
        show(RenameCategoryPrompt(id = row.id, currentName = row.name))
    }

    fun promptRecolor(categoryId: Long) {
        val row = state.value.rowFor(categoryId) ?: return
        show(RecolorPrompt(id = row.id, name = row.name, current = row.colorSlot))
    }

    fun promptDeleteCategory(categoryId: Long) {
        val row = state.value.rowFor(categoryId)?.takeIf { it.deletable } ?: return
        show(ConfirmDeletePrompt(id = row.id, name = row.name))
    }

    fun promptMerge(categoryId: Long) {
        val row = state.value.rowFor(categoryId) ?: return
        val targets = state.value.mergeTargetsFor(categoryId)
        if (targets.isEmpty()) return
        show(ChooseMergeTargetPrompt(fromId = row.id, fromName = row.name, targets = targets))
    }

    /**
     * Blank is caught here rather than at the repository, which throws. An inline message
     * under the field is the spec's answer to an empty name, not a crash.
     */
    fun submitName(raw: String) {
        val prompt = local.value.dialog as? NamePrompt ?: return
        val name = raw.trim()
        if (name.isEmpty()) {
            local.update { it.copy(dialog = prompt.withError(NameError.Blank)) }
            return
        }
        viewModelScope.launch {
            try {
                when (prompt) {
                    is CreateCategoryPrompt -> repository.createCategory(name, prompt.kind)
                    is RenameCategoryPrompt -> repository.renameCategory(prompt.id, name)
                }
                local.update { it.copy(dialog = null) }
            } catch (clash: DuplicateNameException) {
                // Refused, and named. The entry sheet selects the existing record instead,
                // because there the user is filing a transaction and any row under that
                // name will do. Here they are editing the list itself, and quietly folding
                // two names into one loses the one they just typed.
                local.update { it.copy(dialog = prompt.withError(NameError.Duplicate(clash.name))) }
            }
        }
    }

    fun recolor(slot: ColorSlot) {
        val prompt = local.value.dialog as? RecolorPrompt ?: return
        viewModelScope.launch {
            repository.recolorCategory(prompt.id, slot)
            local.update { it.copy(dialog = null) }
        }
    }

    fun setCategoryArchived(categoryId: Long, archived: Boolean) {
        viewModelScope.launch {
            repository.setCategoryArchived(categoryId, archived)
            local.update { it.copy(menuForCategoryId = null) }
        }
    }

    fun confirmDelete() {
        val prompt = local.value.dialog as? ConfirmDeletePrompt ?: return
        viewModelScope.launch {
            // Re-checked against the database, not against the row the menu was built
            // from. The repository throws on a used record, and a transaction could have
            // been filed under it while the dialog was open.
            if (repository.isCategoryDeletable(prompt.id)) repository.deleteCategory(prompt.id)
            local.update { it.copy(dialog = null) }
            recount.update { it + 1 }
        }
    }

    fun chooseMergeTarget(toId: Long) {
        val prompt = local.value.dialog as? ChooseMergeTargetPrompt ?: return
        val target = prompt.targets.firstOrNull { it.id == toId } ?: return
        viewModelScope.launch {
            val affected = repository.transactionCount(prompt.fromId)
            local.update {
                it.copy(
                    dialog = ConfirmMergePrompt(
                        fromId = prompt.fromId,
                        fromName = prompt.fromName,
                        toId = target.id,
                        toName = target.name,
                        affectedCount = affected,
                    ),
                )
            }
        }
    }

    fun confirmMerge() {
        val prompt = local.value.dialog as? ConfirmMergePrompt ?: return
        viewModelScope.launch {
            repository.mergeCategory(prompt.fromId, prompt.toId)
            local.update { it.copy(dialog = null) }
            recount.update { it + 1 }
        }
    }

    /** Drag start: freeze the order being shown so the drag has something to rearrange. */
    fun startReorder() = local.update {
        it.copy(
            pendingOrder = state.value.active.map { row -> row.id },
            menuForCategoryId = null,
        )
    }

    /** Drag: the row being held has crossed [overId]. Nothing is written until the drop. */
    fun moveCategoryOver(draggedId: Long, overId: Long) {
        val order = local.value.pendingOrder ?: state.value.active.map { it.id }
        val from = order.indexOf(draggedId)
        val to = order.indexOf(overId)
        if (from < 0 || to < 0 || from == to) return
        local.update { it.copy(pendingOrder = order.moveItem(from, to)) }
    }

    fun commitReorder() {
        val order = local.value.pendingOrder ?: return
        val kind = local.value.kind
        viewModelScope.launch { persistOrder(order, kind) }
    }

    /**
     * The reorder path that does not need a pointer. Drag is unusable with a screen reader
     * and impossible with a keyboard, so every move a drag can make is also one action.
     */
    fun moveCategory(categoryId: Long, delta: Int) {
        val order = state.value.active.map { it.id }
        val from = order.indexOf(categoryId)
        if (from < 0) return
        val to = from + delta
        if (to !in order.indices) return
        val moved = order.moveItem(from, to)
        val kind = state.value.kind
        local.update { it.copy(pendingOrder = moved, menuForCategoryId = null) }
        viewModelScope.launch { persistOrder(moved, kind) }
    }

    private suspend fun persistOrder(visibleOrder: List<Long>, kind: CategoryKind) {
        val all = repository.allCategories().filter { it.kind == kind }.map { it.id }
        repository.reorderCategories(reorderedIds(all, visibleOrder))
    }

    companion object {
        val Factory = sakuViewModelFactory { CategoryManagerViewModel(categories) }
    }
}
