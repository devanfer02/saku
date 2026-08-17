package com.dvnnfrr.saku.data.repository

import com.dvnnfrr.saku.data.local.AccountBalanceRow
import com.dvnnfrr.saku.data.local.AccountEntity
import com.dvnnfrr.saku.data.local.CategoryEntity
import com.dvnnfrr.saku.data.local.CategoryTotalRow
import com.dvnnfrr.saku.data.local.ImportBatchEntity
import com.dvnnfrr.saku.data.local.SubcategoryEntity
import com.dvnnfrr.saku.data.local.TxnEntity
import com.dvnnfrr.saku.domain.Account
import com.dvnnfrr.saku.domain.AccountType
import com.dvnnfrr.saku.domain.Category
import com.dvnnfrr.saku.domain.CategoryTotal
import com.dvnnfrr.saku.domain.ColorSlot
import com.dvnnfrr.saku.domain.ImportBatch
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.Subcategory
import com.dvnnfrr.saku.domain.Transaction
import kotlinx.datetime.Instant

internal fun Long.asInstant(): Instant = Instant.fromEpochSeconds(this)

/**
 * Out-of-range slots are clamped rather than thrown.
 *
 * `ColorSlot`'s constructor requires 0..7, and these mappers run inside a Flow's
 * `map`, so one bad row would take down a whole screen instead of rendering itself
 * oddly.
 */
private fun Int.asColorSlot(): ColorSlot = ColorSlot(coerceIn(0, ColorSlot.COUNT - 1))

internal fun AccountEntity.toDomain(balance: Long): Account = Account(
    id = id,
    name = name,
    type = type,
    colorSlot = colorSlot.asColorSlot(),
    sortOrder = sortOrder,
    archived = archived,
    openingBalance = Money(openingBalance),
    openingAsOf = openingAsOfEpochSec.asInstant(),
    lastReconciled = lastReconciledEpochSec?.asInstant(),
    balance = Money(balance),
)

/**
 * Carries the real opening fields from the projection.
 *
 * These were placeholders until the review caught it: `byId()` returned the true
 * opening balance while `observeAll()` returned zero for the same account, with
 * nothing marking which was which. An edit sheet prefilled from the list would have
 * silently written the placeholder back.
 */
internal fun AccountBalanceRow.toDomain(): Account = Account(
    id = id,
    name = name,
    type = AccountType.valueOf(type),
    colorSlot = colorSlot.asColorSlot(),
    sortOrder = sortOrder,
    archived = archived,
    openingBalance = Money(openingBalance),
    openingAsOf = openingAsOfEpochSec.asInstant(),
    lastReconciled = lastReconciledEpochSec?.asInstant(),
    balance = Money(balance),
)

internal fun CategoryEntity.toDomain(): Category = Category(
    id = id,
    name = name,
    kind = kind,
    colorSlot = colorSlot.asColorSlot(),
    sortOrder = sortOrder,
    archived = archived,
)

internal fun SubcategoryEntity.toDomain(): Subcategory = Subcategory(
    id = id,
    categoryId = categoryId,
    name = name,
    sortOrder = sortOrder,
    archived = archived,
)

internal fun TxnEntity.toDomain(): Transaction = Transaction(
    id = id,
    occurredAt = occurredAtEpochSec.asInstant(),
    type = type,
    amount = Money(amountIdr),
    accountId = accountId,
    toAccountId = toAccountId,
    categoryId = categoryId,
    subcategoryId = subcategoryId,
    note = note,
    createdAt = createdAtEpochSec.asInstant(),
    importBatchId = importBatchId,
)

internal fun ImportBatchEntity.toDomain(): ImportBatch = ImportBatch(
    id = id,
    fileName = fileName,
    importedAt = importedAtEpochSec.asInstant(),
    rowCount = rowCount,
    insertedCount = insertedCount,
    skippedCount = skippedCount,
    reversedAt = reversedAtEpochSec?.asInstant(),
)

/**
 * Shares in basis points, computed with integer arithmetic and with the rounding
 * remainder given to the largest slice so the column sums to exactly 100%.
 */
internal fun List<CategoryTotalRow>.toDomain(): List<CategoryTotal> {
    val sum = sumOf { it.total }
    if (sum == 0L) {
        return map {
            CategoryTotal(it.categoryId, it.categoryName, it.colorSlot.asColorSlot(), Money(it.total), 0)
        }
    }

    val raw = map { row ->
        CategoryTotal(
            categoryId = row.categoryId,
            name = row.categoryName,
            colorSlot = row.colorSlot.asColorSlot(),
            total = Money(row.total),
            shareBasisPoints = ((row.total * 10_000L) / sum).toInt(),
        )
    }

    val drift = 10_000 - raw.sumOf { it.shareBasisPoints }
    if (drift == 0 || raw.isEmpty()) return raw
    // Rows arrive ordered by amount descending, so index 0 is the largest slice.
    return raw.mapIndexed { index, total ->
        if (index == 0) total.copy(shareBasisPoints = total.shareBasisPoints + drift) else total
    }
}
