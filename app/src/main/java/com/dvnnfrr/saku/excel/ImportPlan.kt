package com.dvnnfrr.saku.excel

import com.dvnnfrr.saku.data.repository.PendingRow
import com.dvnnfrr.saku.domain.DedupHash
import com.dvnnfrr.saku.domain.TxnType

/** A subcategory is only unique within its parent, so both names are the key. */
data class SubcategoryKey(val categoryName: String, val subcategoryName: String)

/**
 * Every name in the file, resolved to the record it will be written against.
 *
 * [transferDestinations] is keyed by the *category* of the transfer group rather than
 * by row: the reference export's `Transfer-Out` rows carry no destination at all, so
 * the only thing they can be grouped by is the category that stood in for one, and the
 * user answers once per group instead of six times.
 */
data class RowMappings(
    val accounts: Map<String, Long>,
    val transferDestinations: Map<String?, Long>,
    val categories: Map<String, Long>,
    val subcategories: Map<SubcategoryKey, Long>,
)

/**
 * What the preview promises and the import then does. [deleted] is a property rather
 * than a field because it is structurally zero: no import path deletes or overwrites an
 * existing transaction, so there is nothing that could ever set it.
 */
data class ImportTally(
    val fileRowCount: Int,
    val parsedRowCount: Int,
    val heldBack: Int,
    val excluded: Int,
    val insertable: Int,
    val alreadyPresent: Int,
    val collapsedInFile: Int,
) {
    val deleted: Int get() = 0
}

/**
 * Turns parsed rows into the shape [com.dvnnfrr.saku.data.repository.ImportRepository]
 * commits, and counts what that commit would do before it runs.
 *
 * Pure and id-only: it holds no repository, so the whole preview arithmetic — dedup,
 * within-file collapse, the tally the user reads before tapping import — is tested
 * against the real export with no database involved.
 */
object ImportPlan {

    /**
     * Hashes for the tally, tolerating names the user has not mapped yet.
     *
     * An unmapped or about-to-be-created account gets a negative placeholder id. That is
     * not a fudge: an account that does not exist holds no transactions, so no hash built
     * on it can collide with a stored one and every such row is genuinely new. A name
     * still awaiting a choice is an estimate — the count settles the moment it is mapped,
     * and import stays disabled until then.
     */
    fun previewHashes(
        rows: List<ParsedRow>,
        accounts: Map<String, Long>,
        transferDestinations: Map<String?, Long>,
    ): List<String> {
        val provisional = mutableMapOf<String, Long>()
        fun idFor(name: String): Long =
            accounts[name] ?: provisional.getOrPut(name) { -(provisional.size + 1L) }

        return rows.map { row ->
            val destination = when {
                row.type != TxnType.TRANSFER -> null
                row.toAccountName != null -> idFor(row.toAccountName)
                else -> transferDestinations[row.categoryName]
            }
            DedupHash.of(
                occurredAt = row.occurredAt,
                type = row.type,
                amount = row.amount,
                accountId = idFor(row.accountName),
                toAccountId = destination,
                categoryName = row.categoryName,
                note = row.note,
            )
        }
    }

    /**
     * The rows to commit. Every name must be mapped by now; a missing one is a
     * programming error rather than something to paper over, because a transfer that
     * silently loses its destination is the exact failure this track exists to prevent.
     */
    fun pendingRows(rows: List<ParsedRow>, mappings: RowMappings): List<PendingRow> =
        rows.map { row ->
            val accountId = requireNotNull(mappings.accounts[row.accountName]) {
                "Account \"${row.accountName}\" was never mapped"
            }
            val destination = when {
                row.type != TxnType.TRANSFER -> null
                row.toAccountName != null -> requireNotNull(mappings.accounts[row.toAccountName]) {
                    "Destination \"${row.toAccountName}\" was never mapped"
                }

                else -> requireNotNull(mappings.transferDestinations[row.categoryName]) {
                    "Transfers in \"${row.categoryName}\" have no destination account"
                }
            }
            val categoryId = row.categoryName?.let { mappings.categories[it] }
            val subcategoryId = row.categoryName
                ?.let { category -> row.subcategoryName?.let { SubcategoryKey(category, it) } }
                ?.let { mappings.subcategories[it] }

            PendingRow(
                occurredAt = row.occurredAt,
                type = row.type,
                amount = row.amount,
                accountId = accountId,
                toAccountId = destination,
                categoryId = categoryId,
                subcategoryId = subcategoryId,
                note = row.note,
                dedupHash = DedupHash.of(
                    occurredAt = row.occurredAt,
                    type = row.type,
                    amount = row.amount,
                    accountId = accountId,
                    toAccountId = destination,
                    categoryName = row.categoryName,
                    note = row.note,
                ),
            )
        }

    /**
     * The tally, computed the same way `commit` filters: existing hashes drop out first,
     * then what remains collapses on duplicate hashes within the file itself. Reversing
     * those two steps would report a repeated purchase as already present.
     */
    fun tally(
        sheet: ParsedSheet,
        hashes: List<String>,
        existing: Set<String>,
        excluded: Int,
    ): ImportTally {
        val notPresent = hashes.filterNot { it in existing }
        val distinct = notPresent.distinct()
        return ImportTally(
            fileRowCount = sheet.fileRowCount,
            parsedRowCount = sheet.rows.size,
            heldBack = sheet.rejected.size,
            excluded = excluded,
            insertable = distinct.size,
            alreadyPresent = hashes.size - notPresent.size,
            collapsedInFile = notPresent.size - distinct.size,
        )
    }
}
