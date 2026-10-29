package com.dvnnfrr.saku.ui.importexport

import com.dvnnfrr.saku.data.repository.PendingRow
import com.dvnnfrr.saku.domain.ImportBatch
import com.dvnnfrr.saku.domain.TxnType
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** What a finished import did, for the screen that reports it. */
data class ImportSummary(
    val fileName: String,
    val added: Int,
    val alreadyHere: Int,
    /** Earliest and latest of the rows that went in; null when none did. */
    val span: DateSpan?,
    /** Rows added per type, in [TxnType] order, leaving out the types with none. */
    val byType: Map<TxnType, Int>,
    /** Every account that gained a row, on either leg of a transfer, by name. */
    val accounts: List<String>,
    val createdCategories: List<String>,
)

/**
 * Folds the committed rows into the result screen's summary.
 *
 * [presentBefore] is the set of hashes stored before the commit. The rows that landed
 * are found the way `commit` finds them: stored hashes drop out first, then repeats
 * within the file collapse to one. The counts come from [batch], which is the
 * repository's own answer, so the headline never disagrees with import history.
 */
fun importSummary(
    batch: ImportBatch,
    rows: List<PendingRow>,
    presentBefore: Set<String>,
    accountNames: Map<Long, String>,
    createdCategories: List<String>,
    zone: TimeZone,
): ImportSummary {
    val landed = rows.filterNot { it.dedupHash in presentBefore }.distinctBy { it.dedupHash }
    val dates = landed.map { it.occurredAt.toLocalDateTime(zone).date }
    val counts = landed.groupingBy { it.type }.eachCount()

    return ImportSummary(
        fileName = batch.fileName,
        added = batch.insertedCount,
        alreadyHere = batch.skippedCount,
        span = if (dates.isEmpty()) null else DateSpan(dates.min(), dates.max()),
        byType = TxnType.entries.mapNotNull { type -> counts[type]?.let { type to it } }.toMap(),
        accounts = landed.flatMap { listOfNotNull(it.accountId, it.toAccountId) }
            .distinct()
            .mapNotNull(accountNames::get)
            .sorted(),
        createdCategories = createdCategories.sorted(),
    )
}
