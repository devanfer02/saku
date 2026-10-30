package com.dvnnfrr.saku.excel

import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.TxnType
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Covers "Skip duplicates and merge into existing months" from
 * specs/excel-interchange/spec.md. Pure arithmetic over the mock export, no database.
 */
class ImportPlanTest {

    private val sheet: ParsedSheet by lazy {
        (ImportParser.parse(XlsxReader.read(Fixture.file), JAKARTA) as ParseOutcome.Parsed).sheet
    }

    private val accounts = mapOf("Card" to 1L, "Cash" to 2L, "OVO" to 3L)
    private val destinations = mapOf<String?, Long>("Reksa Dana" to 4L, "Emas" to 5L)

    /** Scenario: Reviewing an import — 382 rows of which 54 already exist. */
    @Test
    fun `the tally splits new from already present and never reports a deletion`() {
        val hashes = ImportPlan.previewHashes(sheet.rows, accounts, destinations)
        val alreadyThere = hashes.take(54).toSet()

        val tally = ImportPlan.tally(sheet, hashes, alreadyThere, excluded = 0)

        assertEquals(382, tally.fileRowCount)
        assertEquals(54, tally.alreadyPresent)
        assertEquals(328, tally.insertable)
        assertEquals(0, tally.deleted)
        assertEquals(0, tally.collapsedInFile)
    }

    /** Every one of the 382 rows is distinct on the key, so nothing collapses. */
    @Test
    fun `the reference export holds no internal duplicates`() {
        val hashes = ImportPlan.previewHashes(sheet.rows, accounts, destinations)
        assertEquals(382, hashes.distinct().size)
    }

    /**
     * Scenario: Two rows differing only by account are both imported.
     *
     * This is why the account is in the key at all. With it left out, two real parkir
     * entries at the same picked minute hashed identically and the importer dropped one
     * as a duplicate — the reference export has Subcategory and Description empty on
     * every row, so without the account the key collapses to (second, type, amount,
     * category).
     */
    @Test
    fun `two rows differing only by account hash differently`() {
        val fromCard = row(accountName = "Card")
        val fromCash = row(accountName = "Cash")

        val hashes = ImportPlan.previewHashes(listOf(fromCard, fromCash), accounts, destinations)
        assertNotEquals(hashes[0], hashes[1])

        val tally = ImportPlan.tally(
            sheet = sheet.copy(rows = listOf(fromCard, fromCash), fileRowCount = 2),
            hashes = hashes,
            existing = emptySet(),
            excluded = 0,
        )
        assertEquals(2, tally.insertable)
        assertEquals(0, tally.collapsedInFile)
    }

    /** Scenario: A genuinely repeated purchase collapses, and the count is reported. */
    @Test
    fun `two identical rows collapse to one and the collapse is counted`() {
        val rows = listOf(row(sourceRow = 1), row(sourceRow = 2))
        val hashes = ImportPlan.previewHashes(rows, accounts, destinations)
        assertEquals(hashes[0], hashes[1])

        val tally = ImportPlan.tally(
            sheet = sheet.copy(rows = rows, fileRowCount = 2),
            hashes = hashes,
            existing = emptySet(),
            excluded = 0,
        )
        assertEquals(1, tally.insertable)
        assertEquals(1, tally.collapsedInFile)
        assertEquals(0, tally.deleted)
    }

    /**
     * A transfer group's destination is part of the key, so answering the destination
     * question differently produces a different row rather than a silent duplicate.
     */
    @Test
    fun `a transfer's destination changes its hash`() {
        val transfer = row(type = TxnType.TRANSFER, category = "Emas")
        val toShares = ImportPlan.previewHashes(listOf(transfer), accounts, destinations)
        val toOther = ImportPlan.previewHashes(
            listOf(transfer),
            accounts,
            mapOf<String?, Long>("Emas" to 9L),
        )
        assertNotEquals(toShares.single(), toOther.single())
    }

    /**
     * An account the user is about to create cannot hold an existing transaction, so a
     * placeholder id is safe for the tally: the row is new whatever id it ends up with.
     */
    @Test
    fun `an unmapped account still yields a stable hash per name`() {
        val rows = listOf(row(accountName = "Jenius"), row(accountName = "Jenius", sourceRow = 2))
        val hashes = ImportPlan.previewHashes(rows, emptyMap(), destinations)
        assertEquals(hashes[0], hashes[1])
    }

    @Test
    fun `excluded rows are reported separately from held-back ones`() {
        val hashes = ImportPlan.previewHashes(sheet.rows.drop(3), accounts, destinations)
        val tally = ImportPlan.tally(sheet, hashes, emptySet(), excluded = 3)
        assertEquals(3, tally.excluded)
        assertEquals(379, tally.insertable)
    }

    private fun row(
        sourceRow: Int = 1,
        accountName: String = "Card",
        type: TxnType = TxnType.EXPENSE,
        category: String? = "Parkir",
        note: String? = null,
    ) = ParsedRow(
        sourceRow = sourceRow,
        occurredAt = Instant.parse("2026-08-16T11:47:34Z"),
        type = type,
        amount = Money(5_000),
        accountName = accountName,
        toAccountName = null,
        categoryName = category,
        subcategoryName = null,
        note = note,
        discrepancy = null,
    )
}
