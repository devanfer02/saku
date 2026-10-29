package com.dvnnfrr.saku.data.repository

import androidx.room.withTransaction
import com.dvnnfrr.saku.data.local.AccountEntity
import com.dvnnfrr.saku.data.local.CategoryEntity
import com.dvnnfrr.saku.data.local.ImportBatchEntity
import com.dvnnfrr.saku.data.local.SakuDatabase
import com.dvnnfrr.saku.data.local.SubcategoryEntity
import com.dvnnfrr.saku.data.local.TxnEntity
import com.dvnnfrr.saku.domain.ImportBatch
import com.dvnnfrr.saku.excel.BackupContents
import com.dvnnfrr.saku.excel.ExistingRecords
import com.dvnnfrr.saku.excel.RestoreIds
import com.dvnnfrr.saku.excel.RestorePlan
import com.dvnnfrr.saku.excel.nameKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant

/**
 * Import inserts. It never updates, overwrites or deletes an existing transaction —
 * a duplicate is skipped, which is why the preview's deleted count is structurally
 * zero rather than incidentally zero.
 */
class RoomImportRepository(
    private val db: SakuDatabase,
) : ImportRepository {

    private val txnDao = db.txnDao()
    private val batchDao = db.importBatchDao()
    private val accountDao = db.accountDao()
    private val categoryDao = db.categoryDao()
    private val subcategoryDao = db.subcategoryDao()

    /**
     * The same validator the entry sheet goes through.
     *
     * Import previously wrote rows straight to `TxnEntity` with no checks at all,
     * which meant the one shape this app exists to fix could walk right back in: a
     * `Transfer-Out` row with no destination debits its source and credits nobody,
     * so the total drops by the transfer amount. That is exactly the 5.050.800 of
     * investment transfers reading as money gone in the reference data.
     */
    private val validator = RoomTransactionRepository(db)

    override fun observeBatches(): Flow<List<ImportBatch>> =
        batchDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun existingHashes(hashes: List<String>): Set<String> =
        hashes.chunked(SQLITE_VARIABLE_LIMIT)
            .flatMap { txnDao.existingHashes(it) }
            .toSet()

    override suspend fun commit(
        fileName: String,
        rows: List<PendingRow>,
        fileRowCount: Int,
        at: Instant,
    ): ImportBatch = db.withTransaction {
        val alreadyPresent = existingHashes(rows.map { it.dedupHash })
        // Also de-duplicates within the file: two rows identical on the whole key
        // collapse to one, and the preview reports the collapsed count.
        val deduped = rows.filterNot { it.dedupHash in alreadyPresent }
        val fresh = deduped.distinctBy { it.dedupHash }

        // Validated inside the transaction, so one bad row aborts the whole batch
        // rather than leaving a half-imported file behind.
        fresh.forEachIndexed { index, row ->
            try {
                validator.validate(
                    occurredAt = row.occurredAt,
                    type = row.type,
                    amount = row.amount,
                    accountId = row.accountId,
                    toAccountId = row.toAccountId,
                    categoryId = row.categoryId,
                    subcategoryId = row.subcategoryId,
                )
            } catch (invalid: IllegalArgumentException) {
                throw IllegalArgumentException(
                    "Row ${index + 1} of $fileName cannot be imported: ${invalid.message}",
                    invalid,
                )
            }
        }

        val batchId = batchDao.insert(
            ImportBatchEntity(
                fileName = fileName,
                importedAtEpochSec = at.epochSeconds,
                rowCount = fileRowCount,
                insertedCount = fresh.size,
                skippedCount = rows.size - fresh.size,
            ),
        )

        val insertedIds = if (fresh.isEmpty()) {
            emptyList()
        } else {
            txnDao.insertAll(
                fresh.map { row ->
                    TxnEntity(
                        occurredAtEpochSec = row.occurredAt.epochSeconds,
                        type = row.type,
                        amountIdr = row.amount.rupiah,
                        accountId = row.accountId,
                        toAccountId = row.toAccountId,
                        categoryId = row.categoryId,
                        subcategoryId = row.subcategoryId,
                        note = row.note,
                        createdAtEpochSec = at.epochSeconds,
                        importBatchId = batchId,
                        dedupHash = row.dedupHash,
                    )
                },
            )
        }

        // What actually landed, not what was attempted.
        val inserted = insertedIds.count { it > 0 }

        ImportBatch(
            id = batchId,
            fileName = fileName,
            importedAt = at,
            rowCount = fileRowCount,
            insertedCount = inserted,
            skippedCount = rows.size - inserted,
        )
    }

    /**
     * Removes only this batch's rows; hand-entered transactions are never touched.
     * The batch record survives, marked reversed, because import history has to show
     * that the import happened at all.
     */
    override suspend fun reverse(batchId: Long, at: Instant): Int = db.withTransaction {
        // Guarded, and the guard runs first. Reversing twice used to delete zero rows,
        // return zero, and still overwrite reversedAt — so the history screen showed a
        // batch reversed at a moment when nothing happened, and a double tap reported
        // "0 removed" over a batch that had removed 1,540.
        val claimed = batchDao.markReversedIfNot(batchId, at.epochSeconds)
        if (claimed == 0) return@withTransaction 0
        txnDao.deleteByImportBatch(batchId)
    }

    override suspend fun backupSnapshot(): BackupSnapshot = db.withTransaction {
        val records = existingRecords()
        BackupSnapshot(
            accounts = records.accounts,
            categories = records.categories,
            subcategories = records.subcategories,
            transactions = txnDao.all().map { it.toDomain() },
        )
    }

    /**
     * The plan is rebuilt here, inside the transaction, rather than taken from the preview:
     * whatever exists at commit time is what gets matched, so a record created since the
     * preview is reused instead of duplicated.
     *
     * Records are inserted with every setting the backup holds, sort order included, so a
     * restore onto an empty database reproduces the one that wrote the file. [commit] runs
     * in this same transaction, and when it throws on an invalid row the records created
     * above roll back with it.
     */
    override suspend fun restore(
        backup: BackupContents,
        fileName: String,
        at: Instant,
    ): RestoreResult = db.withTransaction {
        val plan = RestorePlan.of(backup, existingRecords())

        val accountIds = plan.matched.accounts.toMutableMap()
        plan.accountsToCreate.forEach { account ->
            accountIds[nameKey(account.name)] = accountDao.insert(
                AccountEntity(
                    name = account.name,
                    type = account.type,
                    openingBalance = account.openingBalance.rupiah,
                    openingAsOfEpochSec = account.openingAsOf.epochSeconds,
                    colorSlot = account.colorSlot.index,
                    sortOrder = account.sortOrder,
                    archived = account.archived,
                    lastReconciledEpochSec = account.lastReconciled?.epochSeconds,
                ),
            )
        }

        val categoryIds = plan.matched.categories.toMutableMap()
        plan.categoriesToCreate.forEach { category ->
            categoryIds[category.ref] = categoryDao.insert(
                CategoryEntity(
                    name = category.name,
                    kind = category.kind,
                    colorSlot = category.colorSlot.index,
                    sortOrder = category.sortOrder,
                    archived = category.archived,
                ),
            )
        }

        val subcategoryIds = plan.matched.subcategories.toMutableMap()
        plan.subcategoriesToCreate.forEach { subcategory ->
            subcategoryIds[subcategory.ref] = subcategoryDao.insert(
                SubcategoryEntity(
                    categoryId = categoryIds.getValue(subcategory.ref.category),
                    name = subcategory.name,
                    sortOrder = subcategory.sortOrder,
                    archived = subcategory.archived,
                ),
            )
        }

        val batch = commit(
            fileName = fileName,
            rows = plan.pendingRows(RestoreIds(accountIds, categoryIds, subcategoryIds)),
            fileRowCount = backup.transactions.fileRowCount,
            at = at,
        )

        RestoreResult(
            accountsCreated = plan.accountsToCreate.size,
            categoriesCreated = plan.categoriesToCreate.size,
            subcategoriesCreated = plan.subcategoriesToCreate.size,
            batch = batch,
        )
    }

    /** One-shot DAO reads only; a Flow's first() inside withTransaction deadlocks. */
    private suspend fun existingRecords(): ExistingRecords = ExistingRecords(
        accounts = accountDao.all().map { it.toDomain(accountDao.balanceOf(it.id) ?: it.openingBalance) },
        categories = categoryDao.all().map { it.toDomain() },
        subcategories = subcategoryDao.all().map { it.toDomain() },
    )

    private companion object {
        // SQLite caps host parameters per statement; chunk IN-clauses below it.
        const val SQLITE_VARIABLE_LIMIT = 900
    }
}
