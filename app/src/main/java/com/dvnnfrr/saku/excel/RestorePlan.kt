package com.dvnnfrr.saku.excel

import com.dvnnfrr.saku.data.repository.PendingRow
import com.dvnnfrr.saku.domain.Account
import com.dvnnfrr.saku.domain.Category
import com.dvnnfrr.saku.domain.DedupHash
import com.dvnnfrr.saku.domain.Subcategory

/** What the database already holds, as the plan needs to see it. Archived records included. */
data class ExistingRecords(
    val accounts: List<Account>,
    val categories: List<Category>,
    val subcategories: List<Subcategory>,
)

/** An account setting the backup holds differently from the same-named account here. */
enum class AccountSetting {
    TYPE,
    OPENING_BALANCE,
    OPENING_DATE,
    COLOUR,
    ARCHIVED,
    LAST_RECONCILED,
}

/** [name] is the existing account's name; its settings are the ones restore keeps. */
data class AccountConflict(val name: String, val settings: List<AccountSetting>)

/** The database id behind every name the transactions use. */
data class RestoreIds(
    val accounts: Map<String, Long>,
    val categories: Map<CategoryRef, Long>,
    val subcategories: Map<SubcategoryRef, Long>,
)

/**
 * What restoring [backup] into a database would create, and what it would leave alone.
 *
 * Pure: it is computed once for the preview from repository reads, and again by the
 * repository inside the restore transaction, so the two cannot drift apart. Records are
 * matched by [nameKey] (accounts by name, categories by name within kind, subcategories
 * by name within their parent) and archived ones match too, the way import does. A
 * matched record is never changed: where an account's settings differ, the difference is
 * listed in [conflicts] and the existing account wins.
 */
data class RestorePlan(
    val backup: BackupContents,
    val accountsToCreate: List<BackupAccount>,
    val categoriesToCreate: List<BackupCategory>,
    val subcategoriesToCreate: List<BackupSubcategory>,
    val conflicts: List<AccountConflict>,
    /** Ids of records that already exist. Records still to be created are absent. */
    val matched: RestoreIds,
) {

    /**
     * One dedup hash per transaction row, for the preview's tally. An account still to be
     * created gets a negative placeholder id, as in [ImportPlan.previewHashes]: it holds no
     * transactions yet, so no hash built on it can match a stored one.
     */
    fun previewHashes(): List<String> {
        val provisional = mutableMapOf<String, Long>()
        fun idFor(name: String): Long {
            val key = nameKey(name)
            return matched.accounts[key] ?: provisional.getOrPut(key) { -(provisional.size + 1L) }
        }
        return backup.transactions.rows.map { row -> hashOf(row, ::idFor) }
    }

    /** The rows to commit once every record exists. A missing id is a programming error. */
    fun pendingRows(ids: RestoreIds): List<PendingRow> {
        fun accountId(name: String): Long =
            requireNotNull(ids.accounts[nameKey(name)]) { "Account \"$name\" was never created" }

        return backup.transactions.rows.map { row ->
            val categoryId = backup.categoryRefOf(row)?.let {
                requireNotNull(ids.categories[it]) { "Category \"${row.categoryName}\" was never created" }
            }
            val subcategoryId = backup.subcategoryRefOf(row)?.let {
                requireNotNull(ids.subcategories[it]) { "Subcategory \"${row.subcategoryName}\" was never created" }
            }
            PendingRow(
                occurredAt = row.occurredAt,
                type = row.type,
                amount = row.amount,
                accountId = accountId(row.accountName),
                toAccountId = row.toAccountName?.let(::accountId),
                categoryId = categoryId,
                subcategoryId = subcategoryId,
                note = row.note,
                dedupHash = hashOf(row, ::accountId),
            )
        }
    }

    /** The same key import uses, so a restored row and an imported one dedup against each other. */
    private fun hashOf(row: ParsedRow, accountId: (String) -> Long): String =
        DedupHash.of(
            occurredAt = row.occurredAt,
            type = row.type,
            amount = row.amount,
            accountId = accountId(row.accountName),
            toAccountId = row.toAccountName?.let(accountId),
            categoryName = row.categoryName,
            note = row.note,
        )

    companion object {

        fun of(backup: BackupContents, existing: ExistingRecords): RestorePlan {
            // Live records listed last so they win the associateBy, as findAnyByName prefers them.
            val accountsByKey = existing.accounts
                .sortedByDescending { it.archived }
                .associateBy { nameKey(it.name) }
            val categoriesByRef = existing.categories
                .sortedByDescending { it.archived }
                .associateBy { CategoryRef(it.kind, nameKey(it.name)) }
            val refByCategoryId = existing.categories.associate { it.id to CategoryRef(it.kind, nameKey(it.name)) }
            val subcategoriesByRef = existing.subcategories
                .sortedByDescending { it.archived }
                .mapNotNull { sub -> refByCategoryId[sub.categoryId]?.let { SubcategoryRef(it, nameKey(sub.name)) to sub } }
                .toMap()

            val accountsToCreate = backup.accounts.filter { nameKey(it.name) !in accountsByKey }
            val categoriesToCreate = backup.categories.filter { it.ref !in categoriesByRef }
            val subcategoriesToCreate = backup.subcategories.filter { it.ref !in subcategoriesByRef }

            val conflicts = backup.accounts.mapNotNull { wanted ->
                val here = accountsByKey[nameKey(wanted.name)] ?: return@mapNotNull null
                differences(wanted, here).takeIf { it.isNotEmpty() }?.let { AccountConflict(here.name, it) }
            }

            return RestorePlan(
                backup = backup,
                accountsToCreate = accountsToCreate,
                categoriesToCreate = categoriesToCreate,
                subcategoriesToCreate = subcategoriesToCreate,
                conflicts = conflicts,
                matched = RestoreIds(
                    accounts = accountsByKey.mapValues { it.value.id },
                    categories = categoriesByRef.mapValues { it.value.id },
                    subcategories = subcategoriesByRef.mapValues { it.value.id },
                ),
            )
        }

        /** Sort order is left out: it is cosmetic, and a seeded account always differs. */
        private fun differences(wanted: BackupAccount, here: Account): List<AccountSetting> = buildList {
            if (wanted.type != here.type) add(AccountSetting.TYPE)
            if (wanted.openingBalance != here.openingBalance) add(AccountSetting.OPENING_BALANCE)
            if (wanted.openingAsOf != here.openingAsOf) add(AccountSetting.OPENING_DATE)
            if (wanted.colorSlot != here.colorSlot) add(AccountSetting.COLOUR)
            if (wanted.archived != here.archived) add(AccountSetting.ARCHIVED)
            if (wanted.lastReconciled != here.lastReconciled) add(AccountSetting.LAST_RECONCILED)
        }
    }
}
