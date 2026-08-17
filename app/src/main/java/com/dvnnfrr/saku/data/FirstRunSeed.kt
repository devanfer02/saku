package com.dvnnfrr.saku.data

import com.dvnnfrr.saku.data.local.AccountEntity
import com.dvnnfrr.saku.data.local.CategoryEntity
import com.dvnnfrr.saku.data.local.SakuDatabase
import com.dvnnfrr.saku.data.local.SubcategoryEntity
import com.dvnnfrr.saku.domain.AccountType
import com.dvnnfrr.saku.domain.CategoryKind
import com.dvnnfrr.saku.domain.ColorSlot
import androidx.room.withTransaction
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

/**
 * What exists the first time the app opens.
 *
 * The sixteen categories are the ones present in a year of reference data,
 * kept in their Indonesian names. They are ordinary user-editable records: nothing
 * here is special-cased, so any of them can be renamed, recoloured or archived.
 */
object FirstRunSeed {

    val DEFAULT_EXPENSE_CATEGORIES = listOf(
        "Pemberian",
        "Makan & Minum",
        "Kebutuhan Kos",
        "Kuliah",
        "Perawatan Diri",
        "Jajan & Hiburan",
        "Baju dan Aksesoris",
        "Kendaraan Pribadi",
        "Kerjaan",
        "Transportasi Umum",
        "Gym",
        "Internet",
        "Servis",
        "Obat",
        "Paket",
        "Parkir",
    )

    val DEFAULT_INCOME_CATEGORIES = listOf("Gaji", "Freelance", "Lainnya")

    /**
     * Starter subcategories for the two categories that are unreadable without a split.
     * Pemberian alone is 50% of spend, and "how much went to Arisan" is currently
     * unanswerable. Import cannot backfill these onto historical rows — those stay
     * blank — but new entries have somewhere to land from day one.
     */
    val DEFAULT_SUBCATEGORIES = mapOf(
        "Pemberian" to listOf("Arisan", "Rumah", "Dika", "Gereja"),
        "Makan & Minum" to listOf("Restoran", "Warung", "Groceries"),
    )

    val DEFAULT_ACCOUNTS = listOf(
        "BCA" to AccountType.BANK,
        "Jago" to AccountType.BANK,
        "Cash" to AccountType.CASH,
        "RDPU SucorInvest" to AccountType.INVESTMENT,
    )

    /**
     * Idempotent, and each table is judged on its own emptiness.
     *
     * Gating the accounts on the *category* table meant a user who pruned the default
     * categories to zero got four duplicate accounts on the next launch — two BCAs in
     * every picker, indistinguishable, with the history split between them.
     *
     * [openingAsOf] is normalised to the start of its local day. Storing the exact
     * instant of first launch meant an install at 23:50 excluded everything logged the
     * next morning against that same date — which is the dominant entry pattern here,
     * since half the reference data was written from memory days late.
     *
     * Every account still shares one cutover. A transfer dated before one account's
     * as-of date but after the other's is counted on one leg only, which invents or
     * destroys money in the Total.
     */
    suspend fun applyIfEmpty(db: SakuDatabase, openingAsOf: Instant, zone: TimeZone) {
        val categoryDao = db.categoryDao()
        val accountDao = db.accountDao()
        val cutover = openingAsOf.toLocalDateTime(zone).date.atStartOfDayIn(zone)

        db.withTransaction {
            val subcategoryDao = db.subcategoryDao()
            val seedCategories = categoryDao.all().isEmpty()
            val seedAccounts = accountDao.all().isEmpty()
            if (!seedCategories && !seedAccounts) return@withTransaction

            if (seedCategories) DEFAULT_EXPENSE_CATEGORIES.forEachIndexed { index, name ->
                val id = categoryDao.insert(
                    CategoryEntity(
                        name = name,
                        kind = CategoryKind.EXPENSE,
                        colorSlot = index % ColorSlot.COUNT,
                        sortOrder = index,
                    ),
                )
                DEFAULT_SUBCATEGORIES[name]?.forEachIndexed { subIndex, subName ->
                    subcategoryDao.insert(
                        SubcategoryEntity(categoryId = id, name = subName, sortOrder = subIndex),
                    )
                }
            }

            if (seedCategories) DEFAULT_INCOME_CATEGORIES.forEachIndexed { index, name ->
                categoryDao.insert(
                    CategoryEntity(
                        name = name,
                        kind = CategoryKind.INCOME,
                        colorSlot = index % ColorSlot.COUNT,
                        sortOrder = index,
                    ),
                )
            }

            if (seedAccounts) DEFAULT_ACCOUNTS.forEachIndexed { index, (name, type) ->
                accountDao.insert(
                    AccountEntity(
                        name = name,
                        type = type,
                        // The owner supplies real figures during first-run setup.
                        openingBalance = 0L,
                        openingAsOfEpochSec = cutover.epochSeconds,
                        colorSlot = index % ColorSlot.COUNT,
                        sortOrder = index,
                    ),
                )
            }
        }
    }
}
