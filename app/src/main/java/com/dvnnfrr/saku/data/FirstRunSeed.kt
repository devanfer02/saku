package com.dvnnfrr.saku.data

import com.dvnnfrr.saku.data.local.AccountEntity
import com.dvnnfrr.saku.data.local.SakuDatabase
import com.dvnnfrr.saku.domain.AccountType
import com.dvnnfrr.saku.domain.ColorSlot
import androidx.room.withTransaction
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

/**
 * What exists the first time the app opens: a `Cash` account and a `Card` account, and
 * nothing else.
 *
 * Decided 30 Oct 2026: Saku is built for anyone, not tailored to its first owner. The
 * earlier seed carried that owner's banks, investment fund, Indonesian categories and
 * subcategories named after family members, none of which mean anything to another
 * user. Two accounts are the minimum the entry sheet needs to save a row.
 *
 * No categories are seeded. A user creates them from the entry sheet's category picker
 * ("New category"), or an import creates every category its file uses. The owner's own
 * history arrives that way, through an import.
 */
object FirstRunSeed {

    /** `Card` matches the account label a Money Manager export uses, so an import maps onto it by name. */
    val DEFAULT_ACCOUNTS = listOf(
        "Cash" to AccountType.CASH,
        "Card" to AccountType.BANK,
    )

    /**
     * Idempotent, judged on the account table's own emptiness.
     *
     * Gating the accounts on the *category* table meant a user who pruned their
     * categories to zero got duplicate accounts on the next launch, indistinguishable in
     * every picker, with the history split between them.
     *
     * [openingAsOf] is normalised to the start of its local day. Storing the exact
     * instant of first launch meant an install at 23:50 excluded everything logged the
     * next morning against that same date, which is the dominant entry pattern here,
     * since half the reference data was written from memory days late.
     *
     * Every account still shares one cutover. A transfer dated before one account's
     * as-of date but after the other's is counted on one leg only, which invents or
     * destroys money in the Total.
     */
    suspend fun applyIfEmpty(db: SakuDatabase, openingAsOf: Instant, zone: TimeZone) {
        val accountDao = db.accountDao()
        val cutover = openingAsOf.toLocalDateTime(zone).date.atStartOfDayIn(zone)

        db.withTransaction {
            if (accountDao.all().isNotEmpty()) return@withTransaction

            DEFAULT_ACCOUNTS.forEachIndexed { index, (name, type) ->
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
