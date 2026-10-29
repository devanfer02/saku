package com.dvnnfrr.saku.ui.importexport

import com.dvnnfrr.saku.data.repository.PendingRow
import com.dvnnfrr.saku.domain.ImportBatch
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.TxnType
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The finished screen's summary, folded from the committed rows with no database: the
 * rows that landed are picked out the way `commit` picks them, then counted.
 */
class ImportSummaryAssemblyTest {

    private val zone = TimeZone.of("Asia/Jakarta")
    private val names = mapOf(1L to "BCA", 2L to "Cash", 3L to "RDPU SucorInvest", 4L to "Gopay")

    private fun row(
        hash: String,
        at: String,
        type: TxnType = TxnType.EXPENSE,
        account: Long = 1L,
        to: Long? = null,
    ) = PendingRow(
        occurredAt = Instant.parse(at),
        type = type,
        amount = Money(10_000),
        accountId = account,
        toAccountId = to,
        categoryId = null,
        subcategoryId = null,
        note = null,
        dedupHash = hash,
    )

    private fun batch(inserted: Int, skipped: Int) = ImportBatch(
        id = 1,
        fileName = "Money Manager_30-10-2026.xlsx",
        importedAt = Instant.parse("2026-10-30T03:00:00Z"),
        rowCount = inserted + skipped,
        insertedCount = inserted,
        skippedCount = skipped,
    )

    private fun summarise(
        rows: List<PendingRow>,
        presentBefore: Set<String> = emptySet(),
        created: List<String> = emptyList(),
        inserted: Int = rows.size,
        skipped: Int = 0,
    ) = importSummary(
        batch = batch(inserted, skipped),
        rows = rows,
        presentBefore = presentBefore,
        accountNames = names,
        createdCategories = created,
        zone = zone,
    )

    @Test
    fun `the counts come from the batch`() {
        val summary = summarise(listOf(row("a", "2026-01-05T03:00:00Z")), inserted = 1, skipped = 7)
        assertEquals("Money Manager_30-10-2026.xlsx", summary.fileName)
        assertEquals(1, summary.added)
        assertEquals(7, summary.alreadyHere)
    }

    @Test
    fun `rows already stored are left out of the breakdown`() {
        val summary = summarise(
            listOf(
                row("old", "2025-07-28T03:00:00Z", type = TxnType.INCOME, account = 2L),
                row("new", "2026-08-16T03:00:00Z"),
            ),
            presentBefore = setOf("old"),
        )
        assertEquals(mapOf(TxnType.EXPENSE to 1), summary.byType)
        assertEquals(listOf("BCA"), summary.accounts)
        assertEquals(DateSpan(LocalDate(2026, 8, 16), LocalDate(2026, 8, 16)), summary.span)
    }

    @Test
    fun `identical rows in the file count once, as commit inserts them once`() {
        val summary = summarise(
            listOf(row("same", "2026-03-01T03:00:00Z"), row("same", "2026-03-01T03:00:00Z")),
        )
        assertEquals(mapOf(TxnType.EXPENSE to 1), summary.byType)
    }

    @Test
    fun `types are counted in a fixed order and empty ones are left out`() {
        val summary = summarise(
            listOf(
                row("t", "2026-03-01T03:00:00Z", type = TxnType.TRANSFER, to = 3L),
                row("i", "2026-03-02T03:00:00Z", type = TxnType.INCOME),
                row("e1", "2026-03-03T03:00:00Z"),
                row("e2", "2026-03-04T03:00:00Z"),
            ),
        )
        assertEquals(
            listOf(TxnType.EXPENSE to 2, TxnType.INCOME to 1, TxnType.TRANSFER to 1),
            summary.byType.toList().sortedBy { it.first.ordinal },
        )
        assertEquals(
            TxnType.entries.filter { it in summary.byType },
            summary.byType.keys.toList(),
        )
    }

    @Test
    fun `both legs of a transfer count as accounts that received rows`() {
        val summary = summarise(
            listOf(
                row("t", "2026-03-01T03:00:00Z", type = TxnType.TRANSFER, account = 4L, to = 3L),
                row("e", "2026-03-02T03:00:00Z", account = 1L),
            ),
        )
        assertEquals(listOf("BCA", "Gopay", "RDPU SucorInvest"), summary.accounts)
    }

    @Test
    fun `the date span is read in the local zone`() {
        // 17:30 UTC on 31 Dec is 00:30 on 1 Jan in Jakarta.
        val summary = summarise(
            listOf(
                row("a", "2025-12-31T17:30:00Z"),
                row("b", "2026-10-29T10:00:00Z"),
            ),
        )
        assertEquals(DateSpan(LocalDate(2026, 1, 1), LocalDate(2026, 10, 29)), summary.span)
    }

    @Test
    fun `an import that added nothing has no span, types or accounts`() {
        val summary = summarise(
            listOf(row("a", "2026-03-01T03:00:00Z")),
            presentBefore = setOf("a"),
            inserted = 0,
            skipped = 1,
        )
        assertNull(summary.span)
        assertEquals(emptyMap<TxnType, Int>(), summary.byType)
        assertEquals(emptyList<String>(), summary.accounts)
    }

    @Test
    fun `created categories are listed alphabetically`() {
        val summary = summarise(
            listOf(row("a", "2026-03-01T03:00:00Z")),
            created = listOf("Pemberian", "Gym", "Kuliah"),
        )
        assertEquals(listOf("Gym", "Kuliah", "Pemberian"), summary.createdCategories)
    }
}
