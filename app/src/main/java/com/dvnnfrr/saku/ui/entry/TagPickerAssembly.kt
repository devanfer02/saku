package com.dvnnfrr.saku.ui.entry

import com.dvnnfrr.saku.domain.Tag
import com.dvnnfrr.saku.domain.TagName

/*
 * Pure assembly for the entry sheet's Tags row and its Add tag picker. Which tags match
 * what is typed, when typing offers a new tag, and what a pick does to the row's chips are
 * all decided here and tested on the JVM. The name rules themselves are `TagName`'s.
 */

/**
 * One chip on the Tags row. [id] is null for a tag typed here that does not exist yet: it is
 * created when the transaction is saved, so a sheet closed without saving leaves no tag.
 */
data class EntryTag(val id: Long?, val name: String)

/** Why the typed text cannot become a tag. The sheet words it. */
enum class TagNameProblem { TOO_LONG, HAS_SEPARATOR }

/**
 * What the picker lists for the typed text.
 *
 * [matches] are tags to tap, best first. [createName] is set when the text, once
 * normalised, names no tag at all, so confirming it makes a new one.
 */
data class TagPickerUi(
    val matches: List<Tag> = emptyList(),
    val createName: String? = null,
    val problem: TagNameProblem? = null,
)

/**
 * Unarchived tags not on the row whose name contains the typed text ignoring case and
 * spaces, those starting with it first, then in the user's order. An archived tag is listed
 * only when the text names it exactly: it is hidden from browsing, not from someone who
 * types its whole name, and offering "Create" there would make a second tag with one name.
 */
fun tagPicker(all: List<Tag>, chosen: List<EntryTag>, typed: String): TagPickerUi {
    val query = TagName.key(typed)
    val chosenKeys = chosen.map { TagName.key(it.name) }.toSet()
    val available = all.filter { TagName.key(it.name) !in chosenKeys }

    val matches = available
        .filter { tag ->
            val key = TagName.key(tag.name)
            if (tag.archived) query.isNotEmpty() && key == query else key.contains(query)
        }
        .sortedWith(
            compareByDescending<Tag> { TagName.key(it.name).startsWith(query) }
                .thenBy { it.sortOrder }
                .thenBy { it.id },
        )

    val normalized = TagName.normalize(typed)
    val exists = query in chosenKeys || all.any { TagName.key(it.name) == query }
    return TagPickerUi(
        matches = matches,
        createName = normalized?.takeIf { !exists },
        problem = problemOf(typed),
    )
}

/** Why [typed] is not a valid name, or null when it is (or is still blank). */
fun problemOf(typed: String): TagNameProblem? {
    val name = typed.trim().replace(Regex("\\s+"), " ")
    return when {
        TagName.SEPARATOR in name -> TagNameProblem.HAS_SEPARATOR
        name.length > TagName.MAX_LENGTH -> TagNameProblem.TOO_LONG
        else -> null
    }
}

/** [tag] added to the row, unless a chip with its name is already there. */
fun List<EntryTag>.plusTag(tag: Tag): List<EntryTag> {
    val key = TagName.key(tag.name)
    return if (any { TagName.key(it.name) == key }) this else this + EntryTag(tag.id, tag.name)
}

/**
 * The typed text added to the row: the existing tag it names in any case, archived ones
 * included, or a new chip to be created on Save. Unchanged for a blank or invalid name.
 */
fun List<EntryTag>.plusTyped(typed: String, all: List<Tag>): List<EntryTag> {
    val name = TagName.normalize(typed) ?: return this
    val key = TagName.key(name)
    if (any { TagName.key(it.name) == key }) return this
    val existing = all.firstOrNull { TagName.key(it.name) == key }
    return this + (existing?.let { EntryTag(it.id, it.name) } ?: EntryTag(null, name))
}

fun List<EntryTag>.minusTag(tag: EntryTag): List<EntryTag> = filterNot { it == tag }

/** A saved row's tag ids as chips, in the user's tag order. */
fun entryTagsOf(ids: Set<Long>, all: List<Tag>): List<EntryTag> =
    all.filter { it.id in ids }
        .sortedWith(compareBy<Tag> { it.sortOrder }.thenBy { it.id })
        .map { EntryTag(it.id, it.name) }
