package com.dvnnfrr.saku.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dvnnfrr.saku.SEED_CUTOVER
import com.dvnnfrr.saku.data.local.SakuDatabase
import com.dvnnfrr.saku.data.repository.PendingRow
import com.dvnnfrr.saku.data.repository.RoomAccountRepository
import com.dvnnfrr.saku.data.repository.RoomImportRepository
import com.dvnnfrr.saku.data.repository.RoomTransactionRepository
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.TxnType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The seeded cutover against the thing it exists to allow: importing history.
 *
 * Every row in the reference export predates any install of this app, so a cutover
 * at install time makes it refuse the data it was built to hold. Pinned here, at the
 * seam between the seed and the importer, because neither side's own tests can see
 * it — the seed's tests pass a date of their choosing, and the importer's tests build
 * accounts to suit the rows.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SeedCutoverTest {

    private lateinit var db: SakuDatabase
    private val zone = TimeZone.of("Asia/Jakarta")

    /** Around the oldest transfer in `data/mock-money-manager.xlsx`. */
    private val backThen = LocalDateTime(2025, 7, 28, 9, 14, 0).toInstant(zone)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SakuDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    /**
     * A transfer, specifically. `validate` refuses one dated before either account's
     * as-of date, and `commit` validates inside its transaction — so one refusal
     * takes the whole 1,200-row batch down with it, not just the offending row.
     */
    @Test
    fun `a seeded install imports a transfer dated a year before it`() = runTest {
        FirstRunSeed.applyIfEmpty(db, openingAsOf = SEED_CUTOVER, zone = zone)
        val accounts = RoomAccountRepository(db).all()
        val bca = accounts.first { it.name == "BCA" }
        val rdpu = accounts.first { it.name == "RDPU SucorInvest" }

        RoomImportRepository(db).commit(
            fileName = "mock-money-manager.xlsx",
            rows = listOf(
                PendingRow(
                    occurredAt = backThen,
                    type = TxnType.TRANSFER,
                    amount = Money(1_000_000),
                    accountId = bca.id,
                    toAccountId = rdpu.id,
                    categoryId = null,
                    subcategoryId = null,
                    note = null,
                    dedupHash = "transfer-1",
                ),
            ),
            fileRowCount = 1,
            at = Instant.fromEpochSeconds(1_755_000_000),
        )

        // Both legs, which is the point: a transfer that lands on one side only
        // invents or destroys money in the total.
        assertEquals(Money(-1_000_000), balanceOf(bca.id))
        assertEquals(Money(1_000_000), balanceOf(rdpu.id))
    }

    /**
     * Importing a row is not enough — it has to reach the balance. Balances count
     * rows on or after the as-of date, so an install-time cutover would leave every
     * account reading zero with a year of history sitting in the list.
     */
    @Test
    fun `a back-dated expense moves the balance it was recorded against`() = runTest {
        FirstRunSeed.applyIfEmpty(db, openingAsOf = SEED_CUTOVER, zone = zone)
        val bca = RoomAccountRepository(db).all().first { it.name == "BCA" }
        val category = db.categoryDao().all().first().id

        RoomTransactionRepository(db).record(
            occurredAt = backThen,
            type = TxnType.EXPENSE,
            amount = Money(250_000),
            accountId = bca.id,
            categoryId = category,
        )

        assertEquals(Money(-250_000), balanceOf(bca.id))
    }

    private suspend fun balanceOf(id: Long): Money =
        RoomAccountRepository(db).observeAll().first().first { it.id == id }.balance
}
