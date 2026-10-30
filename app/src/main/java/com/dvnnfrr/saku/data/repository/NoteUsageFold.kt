package com.dvnnfrr.saku.data.repository

import com.dvnnfrr.saku.data.local.NoteUseRow
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.NoteUsage
import com.dvnnfrr.saku.domain.fold

/** Later first by time; the id breaks a same-second tie the way the list orders rows. */
private val NEWEST: Comparator<NoteUseRow> =
    compareBy<NoteUseRow> { it.occurredAtEpochSec }.thenBy { it.id }

/**
 * Noted transactions of one type, folded into one [NoteUsage] per distinct note.
 *
 * Grouping uses [fold], the same reduction search uses, so "Es Teh" and "es  teh" are
 * one note. Row order does not matter; "most recent" is decided by [NEWEST]. The output
 * is unordered: ranking belongs to the entry sheet's assembly.
 */
fun foldNoteUsage(rows: List<NoteUseRow>): List<NoteUsage> =
    rows.groupBy { fold(it.note) }
        .filterKeys { it.isNotEmpty() }
        .map { (key, uses) ->
            val latest = uses.maxWith(NEWEST)
            NoteUsage(
                key = key,
                note = latest.note.trim(),
                useCount = uses.size,
                lastUsedAt = latest.occurredAtEpochSec.asInstant(),
                lastAmount = Money(latest.amountIdr),
                categoryId = usualCategory(uses),
            )
        }

/** The category used most often, ties to the one used most recently. Null if none was. */
private fun usualCategory(uses: List<NoteUseRow>): Long? =
    uses.filter { it.categoryId != null }
        .groupBy { requireNotNull(it.categoryId) }
        .maxWithOrNull(
            compareBy<Map.Entry<Long, List<NoteUseRow>>> { it.value.size }
                .thenBy(NEWEST) { it.value.maxWith(NEWEST) },
        )
        ?.key
