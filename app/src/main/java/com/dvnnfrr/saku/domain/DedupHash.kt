package com.dvnnfrr.saku.domain

import kotlinx.datetime.Instant
import java.security.MessageDigest

/**
 * Identity of a transaction, used by the importer to skip a row already present.
 *
 * Keyed on timestamp truncated to seconds, type, amount, **account**, destination
 * account, category name and note. Category is hashed by name rather than id because
 * the id may differ between databases.
 *
 * The account is in the key because leaving it out was provably wrong: two real rows
 * differing only by which account paid hashed identically, so the importer dropped
 * one and reported it as a duplicate. The reference export has `Subcategory` and
 * `Description` empty on all 1,200 rows, so without the account the key collapses to
 * (second, type, amount, category) and two Rp 5.000 parkir entries at the same picked
 * minute collide.
 *
 * Note is in the key deliberately: a looser key risks skipping genuinely distinct
 * transactions, and a skipped real row is worse than a visible duplicate.
 *
 * This is an import-time heuristic, not an identity constraint. Two transactions can
 * legitimately share a hash — same fare, same minute, same account — so `txn` must
 * NOT carry a unique index on it.
 */
object DedupHash {

    fun of(
        occurredAt: Instant,
        type: TxnType,
        amount: Money,
        accountId: Long,
        toAccountId: Long?,
        categoryName: String?,
        note: String?,
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        // Length-prefixed rather than delimiter-separated. A delimiter can appear
        // inside imported or pasted text, letting one field's content forge a boundary
        // and collide with a different split of the same bytes.
        listOf(
            occurredAt.truncatedToSeconds().epochSeconds.toString(),
            type.name,
            amount.rupiah.toString(),
            accountId.toString(),
            toAccountId?.toString().orEmpty(),
            categoryName?.trim()?.lowercase().orEmpty(),
            note?.trim().orEmpty(),
        ).forEach { field ->
            val bytes = field.toByteArray(Charsets.UTF_8)
            digest.update("${bytes.size}:".toByteArray(Charsets.UTF_8))
            digest.update(bytes)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
