package com.dvnnfrr.saku.ui.entry

import com.dvnnfrr.saku.domain.Category
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.NoteUsage
import com.dvnnfrr.saku.domain.fold

/** Typed characters, after [fold], before anything is suggested. */
const val NOTE_SUGGESTION_MIN_CHARS = 2

/** The most suggestions the sheet shows at once. */
const val NOTE_SUGGESTION_LIMIT = 5

/**
 * One past note offered under the Note field.
 *
 * [category] is the note's most-used category resolved against the sheet's active
 * categories, so it is null when that category is archived or gone. Picking a
 * suggestion with no category fills the note only (see [applySuggestion]).
 */
data class NoteSuggestion(
    val key: String,
    val note: String,
    val category: Category?,
    val lastAmount: Money,
)

/** More uses first, then more recent, then alphabetical so the order never flickers. */
private val RANK: Comparator<NoteUsage> =
    compareByDescending<NoteUsage> { it.useCount }
        .thenByDescending { it.lastUsedAt }
        .thenBy { it.key }

/**
 * The suggestions for what the user has typed.
 *
 * Nothing under [NOTE_SUGGESTION_MIN_CHARS]. Notes starting with the typed text come
 * before notes that only contain it; each group is ranked by [RANK]; at most
 * [NOTE_SUGGESTION_LIMIT]. An exact match is kept, since picking it is how the user
 * asks for its category and amount.
 */
fun suggestNotes(
    usages: List<NoteUsage>,
    typed: String,
    activeCategories: List<Category>,
): List<NoteSuggestion> {
    val query = fold(typed)
    if (query.length < NOTE_SUGGESTION_MIN_CHARS) return emptyList()

    val (prefix, rest) = usages.partition { it.key.startsWith(query) }
    val contains = rest.filter { query in it.key }
    val ranked = prefix.sortedWith(RANK) + contains.sortedWith(RANK)

    val active = activeCategories.filterNot { it.archived }.associateBy { it.id }
    return ranked.take(NOTE_SUGGESTION_LIMIT).map { usage ->
        NoteSuggestion(
            key = usage.key,
            note = usage.note,
            category = usage.categoryId?.let(active::get),
            lastAmount = usage.lastAmount,
        )
    }
}

/**
 * The form after picking [suggestion]. The rules, recorded in the change's design.md:
 *
 * 1. The note becomes the suggestion's note, as most recently typed.
 * 2. The category becomes the suggestion's usual category.
 * 3. The amount becomes the last amount only while the form's amount is zero with no
 *    pending operator. Anything the user typed wins.
 * 4. Account, destination, date and type never change.
 * 5. A suggestion with no active category fills the note only, amount included: a
 *    note whose category was retired is a weak signal and its amount likely stale.
 */
fun applySuggestion(form: EntryFormState, suggestion: NoteSuggestion): EntryFormState {
    val category = suggestion.category
        ?: return form.copy(note = suggestion.note)

    val amountUntouched = form.amount.value.isZero && !form.amount.hasPendingExpression
    return form.copy(
        note = suggestion.note,
        categoryId = category.id,
        amount = if (amountUntouched) AmountInput.of(suggestion.lastAmount) else form.amount,
    )
}
