package com.dvnnfrr.saku.data.repository

import androidx.room.withTransaction
import com.dvnnfrr.saku.data.local.AccountEntity
import com.dvnnfrr.saku.data.local.SakuDatabase
import com.dvnnfrr.saku.data.local.TxnEntity
import com.dvnnfrr.saku.domain.Account
import com.dvnnfrr.saku.domain.AccountGroup
import com.dvnnfrr.saku.domain.AccountSummary
import com.dvnnfrr.saku.domain.AccountType
import com.dvnnfrr.saku.domain.ColorSlot
import com.dvnnfrr.saku.domain.DedupHash
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.TxnType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant

class RoomAccountRepository(
    private val db: SakuDatabase,
) : AccountRepository {

    private val accountDao = db.accountDao()
    private val txnDao = db.txnDao()

    override fun observeAll(): Flow<List<Account>> =
        accountDao.observeBalances().map { rows -> rows.map { it.toDomain() } }

    override fun observeActive(): Flow<List<Account>> =
        observeAll().map { accounts -> accounts.filterNot { it.archived } }

    /**
     * A liability's stored balance is negative when money is owed, so [AccountSummary.owed]
     * negates the sum to present the amount owed as a positive figure. Folding the raw
     * negative balance straight in made net worth rise when debt was taken on.
     */
    override fun observeSummary(): Flow<AccountSummary> =
        observeActive().map { accounts ->
            val byGroup = accounts.groupBy { it.group }
            val liabilities = byGroup[AccountGroup.OWED].orEmpty()
            AccountSummary(
                spendable = byGroup[AccountGroup.SPENDABLE].orEmpty().sumBalances(),
                invested = byGroup[AccountGroup.INVESTED].orEmpty().sumBalances(),
                // Null, not zero, so the Liabilities row stays hidden until one exists.
                owed = liabilities.takeIf { it.isNotEmpty() }?.let { -it.sumBalances() },
            )
        }

    private fun List<Account>.sumBalances(): Money =
        fold(Money.ZERO) { acc, account -> acc + account.balance }

    override suspend fun byId(id: Long): Account? {
        val entity = accountDao.byId(id) ?: return null
        return entity.toDomain(accountDao.balanceOf(id) ?: entity.openingBalance)
    }

    override suspend fun create(
        name: String,
        type: AccountType,
        openingBalance: Money,
        openingAsOf: Instant,
        colorSlot: ColorSlot?,
    ): Long = db.withTransaction {
        val slot = colorSlot ?: ColorSlot.next(accountDao.allColorSlots().map(::ColorSlot))
        accountDao.insert(
            AccountEntity(
                name = name.trim(),
                type = type,
                openingBalance = openingBalance.rupiah,
                openingAsOfEpochSec = openingAsOf.epochSeconds,
                colorSlot = slot.index,
                // MAX + 1, not the row count: deleting an unused account made the
                // count drop and the next create reuse an existing sortOrder.
                sortOrder = accountDao.nextSortOrder(),
            ),
        )
    }

    /*
     * Single-column updates, not read-modify-write. `@Update` writes every column
     * from a row read earlier, so a rename racing a setOpening wrote the stale
     * opening balance back and the account's balance moved on its own.
     */

    override suspend fun rename(id: Long, name: String) {
        val clean = name.trim()
        require(clean.isNotEmpty()) { "An account needs a name" }
        accountDao.setName(id, clean)
    }

    override suspend fun retype(id: Long, type: AccountType) = accountDao.setType(id, type.name)

    override suspend fun recolor(id: Long, slot: ColorSlot) =
        accountDao.setColorSlot(id, slot.index)

    /**
     * Refuses a cutover that would split an existing transfer.
     *
     * The as-of filter is per account, so moving one account's cutover past a
     * transfer stops that leg counting while the other still does — the Total gains
     * or loses the whole amount. `validate` blocks this on the transaction write
     * path; without the same check here, completing first-run setup after recording
     * a transfer reintroduces it through the back door.
     */
    override suspend fun setOpening(id: Long, openingBalance: Money, openingAsOf: Instant) {
        db.withTransaction {
            val at = openingAsOf.epochSeconds
            val split = accountDao.transfersSplitBy(id, at)
            require(split == 0) {
                val name = accountDao.byId(id)?.name ?: "account $id"
                "Moving $name's opening date to $openingAsOf would orphan $split transfer" +
                    "${if (split == 1) "" else "s"}, inventing or destroying money in the " +
                    "total. Change the transfers first."
            }
            accountDao.setOpening(id, openingBalance.rupiah, at)
        }
    }

    override suspend fun all(): List<Account> = db.withTransaction {
        accountDao.all().map { it.toDomain(accountDao.balanceOf(it.id) ?: it.openingBalance) }
    }

    /**
     * The path import uses to map a file's account names. Matches archived accounts
     * too, unarchiving rather than creating a same-named duplicate — the same rule
     * `CategoryRepository.findOrCreate` follows, and for the same reason: two rows
     * with one name split the history between them.
     */
    override suspend fun findOrCreate(
        name: String,
        type: AccountType,
        openingAsOf: Instant,
    ): Long = db.withTransaction {
        val clean = name.trim()
        require(clean.isNotEmpty()) { "An account needs a name" }
        val existing = accountDao.findAnyByName(clean)
        if (existing != null) {
            if (existing.archived) accountDao.setArchived(existing.id, false)
            existing.id
        } else {
            create(clean, type, Money.ZERO, openingAsOf)
        }
    }

    override suspend fun setArchived(id: Long, archived: Boolean) =
        accountDao.setArchived(id, archived)

    override suspend fun isDeletable(id: Long): Boolean = accountDao.transactionCount(id) == 0

    override suspend fun delete(id: Long) {
        check(isDeletable(id)) {
            "Account $id holds transactions; archive it instead so history stays intact"
        }
        accountDao.deleteUnused(id)
    }

    /**
     * Writes the difference as a visible ADJUSTMENT rather than overwriting the balance.
     * The size of these, on Cash especially, measures how much spending goes untracked,
     * which is the whole reason the flow exists.
     *
     * Atomic, and the adjustment lands before the timestamp. Stamping first meant a
     * failed insert left the account reading "reconciled just now" with no correction
     * written, which is precisely the drift the staleness flag exists to surface.
     */
    override suspend fun reconcile(id: Long, trueBalance: Money, at: Instant): Long? =
        db.withTransaction {
            val entity = accountDao.byId(id) ?: return@withTransaction null
            // balanceOf, not observeBalances().first(): the latter excluded archived
            // accounts and fell back to the opening balance, inventing a correction.
            val derived = accountDao.balanceOf(id) ?: entity.openingBalance
            val difference = trueBalance - Money(derived)

            // Never before the cutover. balanceOf ignores anything earlier, so an
            // adjustment dated before openingAsOf sat in the ledger doing nothing while
            // the account read "reconciled just now" and the derived balance stayed
            // wrong — the same silent-no-op this method was already fixed for once.
            val adjustmentAt = maxOf(at.epochSeconds, entity.openingAsOfEpochSec)

            val adjustmentId = if (difference.isZero) {
                null
            } else {
                txnDao.insert(
                    TxnEntity(
                        occurredAtEpochSec = adjustmentAt,
                        type = TxnType.ADJUSTMENT,
                        // Signed: a downward correction is a negative addend.
                        amountIdr = difference.rupiah,
                        accountId = id,
                        note = "Reconciled ${entity.name}",
                        createdAtEpochSec = at.epochSeconds,
                        dedupHash = DedupHash.of(
                            at, TxnType.ADJUSTMENT, difference, id, null, null,
                            "reconcile-$id-${at.epochSeconds}",
                        ),
                    ),
                )
            }

            accountDao.stampReconciled(id, at.epochSeconds)
            adjustmentId
        }
}
