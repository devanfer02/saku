package com.dvnnfrr.saku.ui.importexport

import com.dvnnfrr.saku.excel.DatabaseAccountRow
import com.dvnnfrr.saku.excel.DatabaseCategoryRow
import com.dvnnfrr.saku.excel.DatabaseImport
import com.dvnnfrr.saku.excel.DatabaseReadOutcome
import com.dvnnfrr.saku.excel.DatabaseRows
import com.dvnnfrr.saku.excel.DatabaseTxnRow
import com.dvnnfrr.saku.excel.ExistingRecords
import com.dvnnfrr.saku.excel.JAKARTA
import com.dvnnfrr.saku.excel.RestorePlan
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

/** [databaseImportPreview] without a database or a view model. */
class DatabaseImportAssemblyTest {

    private val cash = DatabaseAccountRow(1, "Cash", "CASH", 0, 0, 0, 0, false, null)
    private val makan = DatabaseCategoryRow(1, "Makan", "EXPENSE", 0, 0, false)

    private fun expense(id: Long, at: Instant) =
        DatabaseTxnRow(id, at.epochSeconds, "EXPENSE", 10_000, 1, null, 1, null, null)

    private fun planOf(transactions: List<DatabaseTxnRow>): RestorePlan {
        val outcome = DatabaseImport.toBackup(DatabaseRows(listOf(cash), listOf(makan), emptyList(), transactions), 1)
        val backup = (outcome as DatabaseReadOutcome.Read).contents
        return RestorePlan.of(backup, ExistingRecords(emptyList(), emptyList(), emptyList()))
    }

    @Test
    fun `the date range is read on the phone's calendar`() {
        // 23:30 UTC is already the next morning in Jakarta.
        val plan = planOf(
            listOf(
                expense(1, Instant.parse("2026-03-31T23:30:00Z")),
                expense(2, Instant.parse("2026-01-01T01:00:00Z")),
            ),
        )

        val preview = databaseImportPreview("f.db", plan, plan.previewHashes(), emptySet(), JAKARTA)

        assertEquals(LocalDate(2026, 1, 1), preview.firstDate)
        assertEquals(LocalDate(2026, 4, 1), preview.lastDate)
        assertEquals(2, preview.transactionsInFile)
        assertEquals(2, preview.newTransactions)
    }

    @Test
    fun `stored hashes count as already present`() {
        val plan = planOf(listOf(expense(1, Instant.parse("2026-01-01T01:00:00Z"))))
        val hashes = plan.previewHashes()

        val preview = databaseImportPreview("f.db", plan, hashes, hashes.toSet(), JAKARTA)

        assertEquals(0, preview.newTransactions)
        assertEquals(1, preview.alreadyPresent)
    }

    @Test
    fun `a file with records but no transactions has no dates and still has something new`() {
        val plan = planOf(emptyList())

        val preview = databaseImportPreview("f.db", plan, plan.previewHashes(), emptySet(), JAKARTA)

        assertNull(preview.firstDate)
        assertNull(preview.lastDate)
        assertEquals(1, preview.newAccounts)
        assertTrue(preview.hasAnythingNew)
    }
}
