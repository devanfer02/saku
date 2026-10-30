package com.dvnnfrr.saku.ui.categories

import androidx.compose.runtime.Immutable
import com.dvnnfrr.saku.domain.Category
import com.dvnnfrr.saku.domain.CategoryKind
import com.dvnnfrr.saku.domain.ColorSlot

/*
 * The screen's shape, and the rules that are worth testing without a database or a
 * composition: ordering, deletability and which merge targets are legal.
 */

@Immutable
data class CategoryRow(
    val category: Category,
    val transactionCount: Int,
) {
    val id: Long get() = category.id
    val name: String get() = category.name
    val kind: CategoryKind get() = category.kind
    val colorSlot: ColorSlot get() = category.colorSlot
    val archived: Boolean get() = category.archived

    /**
     * Only a category nothing refers to can be deleted. Its stored subcategories, which
     * the app no longer shows, cascade with it; a subcategorised transaction always
     * carries its parent category, so none of them can be in use either.
     */
    val deletable: Boolean get() = transactionCount == 0
}

/** Why a name was refused. Both are shown inline, under the field. */
sealed interface NameError {
    data object Blank : NameError

    data class Duplicate(val name: String) : NameError
}

sealed interface CategoryDialog

/**
 * The dialogs that ask for a name. They share a submit path and therefore share an
 * error slot, so a clash reported by one cannot be rendered differently by another.
 */
sealed interface NamePrompt : CategoryDialog {
    val error: NameError?

    fun withError(error: NameError?): NamePrompt
}

data class CreateCategoryPrompt(
    val kind: CategoryKind,
    override val error: NameError? = null,
) : NamePrompt {
    override fun withError(error: NameError?) = copy(error = error)
}

data class RenameCategoryPrompt(
    val id: Long,
    val currentName: String,
    override val error: NameError? = null,
) : NamePrompt {
    override fun withError(error: NameError?) = copy(error = error)
}

data class RecolorPrompt(
    val id: Long,
    val name: String,
    val current: ColorSlot,
) : CategoryDialog

data class ConfirmDeletePrompt(
    val id: Long,
    val name: String,
) : CategoryDialog

data class ChooseMergeTargetPrompt(
    val fromId: Long,
    val fromName: String,
    val targets: List<CategoryRow>,
) : CategoryDialog

/**
 * [affectedCount] is read before the merge runs and shown in the confirmation, because
 * the rows a merge moves are not on screen and the user has no other way to see how much
 * is about to change hands.
 */
data class ConfirmMergePrompt(
    val fromId: Long,
    val fromName: String,
    val toId: Long,
    val toName: String,
    val affectedCount: Int,
) : CategoryDialog

@Immutable
data class CategoryManagerUiState(
    val kind: CategoryKind = CategoryKind.EXPENSE,
    val active: List<CategoryRow> = emptyList(),
    val archived: List<CategoryRow> = emptyList(),
    val showArchived: Boolean = false,
    val menuForCategoryId: Long? = null,
    val dialog: CategoryDialog? = null,
    /** True while the first read is in flight, so an empty list is not mistaken for none. */
    val loading: Boolean = true,
) {
    val isEmpty: Boolean get() = active.isEmpty() && archived.isEmpty()

    fun rowFor(id: Long): CategoryRow? =
        active.firstOrNull { it.id == id } ?: archived.firstOrNull { it.id == id }

    /**
     * Merge targets: same kind, still active, and not the source itself.
     *
     * Kind is the load-bearing filter. Folding an expense category into an income one
     * would flip the sign of every transaction it moved.
     */
    fun mergeTargetsFor(id: Long): List<CategoryRow> = active.filterNot { it.id == id }
}

fun buildCategoryRows(
    categories: List<Category>,
    categoryCounts: Map<Long, Int>,
): List<CategoryRow> = categories.map { category ->
    CategoryRow(category = category, transactionCount = categoryCounts[category.id] ?: 0)
}

/** Moves one element. Clamped rather than throwing, because a drag can outrun the list. */
fun <T> List<T>.moveItem(from: Int, to: Int): List<T> {
    if (from !in indices) return this
    val target = to.coerceIn(0, lastIndex)
    if (from == target) return this
    val copy = toMutableList()
    copy.add(target, copy.removeAt(from))
    return copy
}

/**
 * Rewrites [visibleOrder] into [all], leaving every row that is not visible on the index
 * it already held.
 *
 * Archived categories are hidden but still carry a sort position, and the spec requires
 * one to come back where it was. Persisting the visible order alone would renumber from
 * zero and drag the archived rows to the end of the list behind the user's back.
 */
fun reorderedIds(all: List<Long>, visibleOrder: List<Long>): List<Long> {
    val moving = visibleOrder.toSet()
    val queue = ArrayDeque(visibleOrder.filter { it in all })
    return all.map { id -> if (id in moving) queue.removeFirstOrNull() ?: id else id }
}

/** Applies an order a drag is still painting, keeping anything it does not name at the end. */
fun List<CategoryRow>.applyOrder(order: List<Long>?): List<CategoryRow> {
    if (order == null) return this
    val position = order.withIndex().associate { (index, id) -> id to index }
    return sortedBy { position[it.id] ?: (order.size + indexOf(it)) }
}
