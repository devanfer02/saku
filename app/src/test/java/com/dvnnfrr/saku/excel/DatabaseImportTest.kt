package com.dvnnfrr.saku.excel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pure half of the database import: the header check and the row checks. */
class DatabaseImportTest {

    private val hash = "bfb8f34edf6ae031f2e4cb76c266b385"

    private fun account(id: Long, name: String, type: String = "CASH", colorSlot: Int = 0) =
        DatabaseAccountRow(id, name, type, 0, 0, colorSlot, id.toInt(), false, null)

    private fun category(id: Long, name: String, kind: String = "EXPENSE") =
        DatabaseCategoryRow(id, name, kind, 0, id.toInt(), false)

    private fun txn(id: Long, type: String, accountId: Long = 1, categoryId: Long? = null, subcategoryId: Long? = null) =
        DatabaseTxnRow(id, 1_767_582_000, type, 1_000, accountId, null, categoryId, subcategoryId, null)

    private fun rows(
        accounts: List<DatabaseAccountRow> = listOf(account(1, "Cash")),
        categories: List<DatabaseCategoryRow> = emptyList(),
        subcategories: List<DatabaseSubcategoryRow> = emptyList(),
        transactions: List<DatabaseTxnRow> = emptyList(),
    ) = DatabaseRows(accounts, categories, subcategories, transactions)

    private fun refusalOf(rows: DatabaseRows): DatabaseRefusal? =
        (DatabaseImport.toBackup(rows, userVersion = 1) as? DatabaseReadOutcome.Refused)?.refusal

    // -- Header ---------------------------------------------------------------------

    @Test
    fun `a matching version and hash pass`() {
        assertNull(DatabaseImport.refusalFor(DatabaseHeader(1, hash), 1, hash))
    }

    @Test
    fun `a different version is named with both numbers`() {
        assertEquals(
            DatabaseRefusal.DifferentVersion(found = 2, expected = 1),
            DatabaseImport.refusalFor(DatabaseHeader(2, hash), 1, hash),
        )
    }

    @Test
    fun `a missing master table wins over the version, since Room never wrote the file`() {
        assertEquals(DatabaseRefusal.NotSaku, DatabaseImport.refusalFor(DatabaseHeader(0, null), 1, hash))
    }

    @Test
    fun `the same version with another hash is another layout`() {
        assertEquals(
            DatabaseRefusal.DifferentLayout,
            DatabaseImport.refusalFor(DatabaseHeader(1, "0".repeat(32)), 1, hash),
        )
    }

    // -- Rows -----------------------------------------------------------------------

    @Test
    fun `two accounts of one name in different case are refused`() {
        val refusal = refusalOf(rows(accounts = listOf(account(1, "Cash"), account(2, " cash "))))

        assertEquals(DatabaseRefusal.DuplicateName("account", " cash "), refusal)
    }

    @Test
    fun `an expense and an income category may share a name`() {
        val outcome = DatabaseImport.toBackup(
            rows(categories = listOf(category(1, "Lain-lain", "EXPENSE"), category(2, "Lain-lain", "INCOME"))),
            userVersion = 1,
        )

        assertTrue(outcome is DatabaseReadOutcome.Read)
    }

    @Test
    fun `two categories of one name and kind are refused`() {
        val refusal = refusalOf(rows(categories = listOf(category(1, "Makan"), category(2, "makan"))))

        assertEquals(DatabaseRefusal.DuplicateName("category", "makan"), refusal)
    }

    @Test
    fun `a colour slot outside the palette is refused`() {
        val refusal = refusalOf(rows(accounts = listOf(account(4, "Cash", colorSlot = 8))))

        assertEquals(DatabaseRefusal.InvalidValue("account", 4, "colorSlot"), refusal)
    }

    @Test
    fun `an unknown account type is refused`() {
        val refusal = refusalOf(rows(accounts = listOf(account(2, "Card", type = "Card"))))

        assertEquals(DatabaseRefusal.InvalidValue("account", 2, "type"), refusal)
    }

    @Test
    fun `an expense carrying an income category is refused rather than recategorised`() {
        val refusal = refusalOf(
            rows(
                categories = listOf(category(1, "Gaji", "INCOME")),
                transactions = listOf(txn(5, "EXPENSE", categoryId = 1)),
            ),
        )

        assertEquals(DatabaseRefusal.CategoryKindMismatch(5), refusal)
    }

    @Test
    fun `a transfer carrying an income category is refused when an expense one shares its name`() {
        val refusal = refusalOf(
            rows(
                accounts = listOf(account(1, "Cash"), account(2, "BCA")),
                categories = listOf(category(1, "Lain-lain", "EXPENSE"), category(2, "Lain-lain", "INCOME")),
                transactions = listOf(txn(6, "TRANSFER", categoryId = 2).copy(toAccountId = 2)),
            ),
        )

        assertEquals(DatabaseRefusal.CategoryKindMismatch(6), refusal)
    }

    @Test
    fun `a subcategory of another category is refused on the transaction`() {
        val refusal = refusalOf(
            rows(
                categories = listOf(category(1, "Makan"), category(2, "Pemberian")),
                subcategories = listOf(DatabaseSubcategoryRow(1, categoryId = 2, name = "Arisan", sortOrder = 0, archived = false)),
                transactions = listOf(txn(8, "EXPENSE", categoryId = 1, subcategoryId = 1)),
            ),
        )

        assertEquals(DatabaseRefusal.InvalidValue("txn", 8, "subcategoryId"), refusal)
    }

    @Test
    fun `a subcategory whose parent is missing is refused`() {
        val refusal = refusalOf(
            rows(subcategories = listOf(DatabaseSubcategoryRow(3, categoryId = 9, name = "Arisan", sortOrder = 0, archived = false))),
        )

        assertEquals(DatabaseRefusal.MissingReference("subcategory", 3, "categoryId"), refusal)
    }
}
