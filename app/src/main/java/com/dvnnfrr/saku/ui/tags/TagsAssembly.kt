package com.dvnnfrr.saku.ui.tags

import com.dvnnfrr.saku.domain.Tag
import com.dvnnfrr.saku.domain.TagName

/*
 * Pure assembly for the Tags screen: rows with their counts, live before archived, and
 * whether a typed rename can be saved. No Compose, no Android.
 */

data class TagRowUi(
    val id: Long,
    val name: String,
    val count: Int,
    val archived: Boolean,
)

/** Why a rename cannot be saved. [TAKEN] carries the other tag's name for the message. */
sealed interface RenameProblem {
    data object Invalid : RenameProblem

    data class Taken(val name: String) : RenameProblem
}

/** The open edit sheet: the tag as stored, and the name being typed. */
data class TagEditUi(
    val id: Long,
    val name: String,
    val draft: String,
    val archived: Boolean,
    val problem: RenameProblem? = null,
) {
    /** Save is offered only for a different, valid name; a case change counts as different. */
    val canSave: Boolean get() = problem == null && TagName.normalize(draft)?.let { it != name } == true
}

data class TagsUiState(
    val active: List<TagRowUi> = emptyList(),
    val archived: List<TagRowUi> = emptyList(),
    val editing: TagEditUi? = null,
    val loading: Boolean = true,
) {
    val isEmpty: Boolean get() = active.isEmpty() && archived.isEmpty()
}

/** Rows in the user's tag order, live tags first, each with its transaction count. */
fun assembleTags(tags: List<Tag>, counts: Map<Long, Int>, editing: TagEditUi?): TagsUiState {
    val rows = tags
        .sortedWith(compareBy<Tag> { it.sortOrder }.thenBy { it.id })
        .map { TagRowUi(it.id, it.name, counts[it.id] ?: 0, it.archived) }
    return TagsUiState(
        active = rows.filterNot { it.archived },
        archived = rows.filter { it.archived },
        editing = editing,
        loading = false,
    )
}

/**
 * What stops [draft] being [id]'s new name: not a valid tag name, or another tag's name in
 * any case. The tag's own name in another case is allowed; that is how a typo in case is fixed.
 */
fun renameProblem(draft: String, id: Long, tags: List<Tag>): RenameProblem? {
    if (draft.isBlank()) return null
    val name = TagName.normalize(draft) ?: return RenameProblem.Invalid
    val key = TagName.key(name)
    return tags.firstOrNull { it.id != id && TagName.key(it.name) == key }?.let { RenameProblem.Taken(it.name) }
}
