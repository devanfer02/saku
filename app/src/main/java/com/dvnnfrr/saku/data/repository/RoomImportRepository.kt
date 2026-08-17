package com.dvnnfrr.saku.data.repository

import androidx.room.withTransaction
import com.dvnnfrr.saku.data.local.ImportBatchEntity
import com.dvnnfrr.saku.data.local.SakuDatabase
import com.dvnnfrr.saku.data.local.TxnEntity
import com.dvnnfrr.saku.domain.ImportBatch
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

    private companion object {
        // SQLite caps host parameters per statement; chunk IN-clauses below it.
        const val SQLITE_VARIABLE_LIMIT = 900
    }
}
