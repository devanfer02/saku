package com.dvnnfrr.saku.domain

import kotlinx.datetime.Instant

/**
 * One distinct note of one transaction type, with what its history says about it.
 *
 * The entry sheet's note autocomplete ranks these and fills a pick from them.
 */
data class NoteUsage(
    /** [fold] of the note: what grouping and matching compare. */
    val key: String,
    /** The note as most recently typed, trimmed. */
    val note: String,
    val useCount: Int,
    val lastUsedAt: Instant,
    /** The amount of the most recent use. */
    val lastAmount: Money,
    /** The category used most often with this note, ties to the most recent; null if none. */
    val categoryId: Long?,
)
