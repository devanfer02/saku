package com.dvnnfrr.saku.excel

import com.dvnnfrr.saku.domain.AccountType
import com.dvnnfrr.saku.domain.CategoryKind
import com.dvnnfrr.saku.domain.ColorSlot
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.Subcategory
import com.dvnnfrr.saku.excel.BackupSheetsTest.Companion.account
import com.dvnnfrr.saku.excel.BackupSheetsTest.Companion.category
import com.dvnnfrr.saku.excel.BackupSheetsTest.Companion.snapshot
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The restore plan with no database: what it would create, what it matches, and which
 * account settings it reports as kept. The repository runs this same function inside its
 * transaction, so these cases are what a restore does, not only what the preview says.
 */
class RestorePlanTest {

    private val backup: BackupContents =
        (BackupSheets.parse(BackupSheets.build(snapshot(), "1.0", Instant.parse("2026-10-30T02:15:00Z"), JAKARTA))
            as BackupReadOutcome.Read).contents

    @Test
    fun `onto an empty database every record is new`() {
        val plan = RestorePlan.of(backup, ExistingRecords(emptyList(), emptyList(), emptyList()))

        assertEquals(3, plan.accountsToCreate.size)
        assertEquals(3, plan.categoriesToCreate.size)
        assertEquals(2, plan.subcategoriesToCreate.size)
        assertEquals(emptyList<AccountConflict>(), plan.conflicts)
        assertEquals(4, plan.previewHashes().distinct().size)
    }

    @Test
    fun `names match ignoring case and surrounding or repeated whitespace`() {
        val existing = ExistingRecords(
            accounts = listOf(account(7, " bca ", AccountType.BANK, opening = 2_500_000)),
            categories = listOf(category(8, "makan  &  minum", CategoryKind.EXPENSE)),
            subcategories = listOf(Subcategory(9, categoryId = 8, name = "WARUNG", sortOrder = 0, archived = false)),
        )

        val plan = RestorePlan.of(backup, existing)

        assertEquals(listOf("Cash", "Old wallet"), plan.accountsToCreate.map { it.name })
        assertEquals(listOf("Gaji", "Kos lama"), plan.categoriesToCreate.map { it.name })
        assertEquals(listOf("Restoran"), plan.subcategoriesToCreate.map { it.name })
        assertEquals(7L, plan.matched.accounts["bca"])
    }

    /** A category of the same name but the other kind is a different category. */
    @Test
    fun `a category matches only within its kind`() {
        val existing = ExistingRecords(
            accounts = emptyList(),
            categories = listOf(category(8, "Gaji", CategoryKind.EXPENSE)),
            subcategories = emptyList(),
        )
        val plan = RestorePlan.of(backup, existing)
        assertTrue(plan.categoriesToCreate.any { it.name == "Gaji" && it.kind == CategoryKind.INCOME })
    }

    @Test
    fun `an archived record matches, so restore does not create a same-named twin`() {
        val existing = ExistingRecords(
            accounts = listOf(account(7, "Old wallet", AccountType.CASH).copy(archived = true)),
            categories = emptyList(),
            subcategories = emptyList(),
        )
        val plan = RestorePlan.of(backup, existing)
        assertTrue(plan.accountsToCreate.none { it.name == "Old wallet" })
    }

    /** Scenario: Restore onto a database with data, at the plan level. */
    @Test
    fun `an account that differs is listed with each differing setting and not recreated`() {
        val existing = ExistingRecords(
            accounts = listOf(
                account(7, "BCA", AccountType.INVESTMENT, opening = 100).copy(colorSlot = ColorSlot(5)),
            ),
            categories = emptyList(),
            subcategories = emptyList(),
        )

        val plan = RestorePlan.of(backup, existing)

        assertEquals(
            listOf(
                AccountConflict(
                    "BCA",
                    listOf(AccountSetting.TYPE, AccountSetting.OPENING_BALANCE, AccountSetting.LAST_RECONCILED),
                ),
            ),
            plan.conflicts,
        )
        assertTrue(plan.accountsToCreate.none { it.name == "BCA" })
    }

    @Test
    fun `pending rows resolve every name to the ids given and hash like the preview`() {
        val plan = RestorePlan.of(backup, ExistingRecords(emptyList(), emptyList(), emptyList()))
        val ids = RestoreIds(
            accounts = mapOf("bca" to 1L, "cash" to 2L, "old wallet" to 3L),
            categories = backup.categories.mapIndexed { index, it -> it.ref to 10L + index }.toMap(),
            subcategories = backup.subcategories.mapIndexed { index, it -> it.ref to 20L + index }.toMap(),
        )

        val rows = plan.pendingRows(ids)

        val expense = rows.single { it.note == "nasi padang" }
        assertEquals(1L, expense.accountId)
        assertEquals(10L, expense.categoryId)
        assertEquals(20L, expense.subcategoryId)
        val transfer = rows.single { it.toAccountId != null }
        assertEquals(2L, transfer.toAccountId)
        assertEquals(Money(-12_000), rows.single { it.amount.rupiah < 0 }.amount)
    }
}
