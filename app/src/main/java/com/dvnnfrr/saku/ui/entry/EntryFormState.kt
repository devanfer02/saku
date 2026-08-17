package com.dvnnfrr.saku.ui.entry

import com.dvnnfrr.saku.domain.CategoryKind
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.Transaction
import com.dvnnfrr.saku.domain.TxnType
import kotlinx.datetime.LocalDateTime

/** Which field an inline message hangs off. */
enum class EntryField { AMOUNT, CATEGORY, ACCOUNT, DESTINATION }

/**
 * Why a save is blocked.
 *
 * An enum rather than a string: this file carries no `R` reference, so it stays
 * testable on the JVM and reusable if `ui/` is ever split into its own module. The
 * screen maps each case to copy.
 */
enum class EntryProblem(val field: EntryField) {
    AMOUNT_MISSING(EntryField.AMOUNT),
    AMOUNT_NOT_POSITIVE(EntryField.AMOUNT),
    CATEGORY_MISSING(EntryField.CATEGORY),
    ACCOUNT_MISSING(EntryField.ACCOUNT),
    DESTINATION_MISSING(EntryField.DESTINATION),
    DESTINATION_SAME_AS_SOURCE(EntryField.DESTINATION),
}

/**
 * Everything the entry sheet holds.
 *
 * The state transitions below are the interesting part. Switching type has to keep
 * what still makes sense and drop what does not, and getting that wrong either loses
 * the user's typing or saves a transaction with a stale field attached — a transfer
 * carrying a category, say, which would then show up in the stats screen as spending.
 */
data class EntryFormState(
    /** Null while recording; set while editing an existing transaction. */
    val id: Long? = null,
    val type: TxnType = TxnType.EXPENSE,
    val amount: AmountInput = AmountInput(),
    val occurredAt: LocalDateTime,
    val accountId: Long? = null,
    val toAccountId: Long? = null,
    val categoryId: Long? = null,
    val subcategoryId: Long? = null,
    val note: String = "",
) {
    val isEditing: Boolean get() = id != null

    /** Which category list the picker should show. Null for a transfer, which has none. */
    val categoryKind: CategoryKind?
        get() = when (type) {
            TxnType.EXPENSE -> CategoryKind.EXPENSE
            TxnType.INCOME -> CategoryKind.INCOME
            TxnType.TRANSFER, TxnType.ADJUSTMENT -> null
        }

    /**
     * Amount, date, time and note survive a type change; the fields that belong to
     * the old type do not.
     */
    fun withType(next: TxnType): EntryFormState {
        if (next == type) return this
        val leavingTransfer = type == TxnType.TRANSFER
        val enteringTransfer = next == TxnType.TRANSFER
        return copy(
            type = next,
            // A transfer has no category, and an income category is not an expense
            // category, so the selection cannot survive either move.
            categoryId = if (enteringTransfer || categoryKindChanges(next)) null else categoryId,
            subcategoryId = if (enteringTransfer || categoryKindChanges(next)) null else subcategoryId,
            toAccountId = if (leavingTransfer) null else toAccountId,
        )
    }

    private fun categoryKindChanges(next: TxnType): Boolean {
        val nextKind = when (next) {
            TxnType.EXPENSE -> CategoryKind.EXPENSE
            TxnType.INCOME -> CategoryKind.INCOME
            else -> null
        }
        return nextKind != categoryKind
    }

    /**
     * A subcategory belongs to exactly one category, so changing the category drops
     * it. [recalled] is the last subcategory used with the new category, which the
     * caller looks up; passing null simply leaves the field empty.
     */
    fun withCategory(next: Long?, recalled: Long? = null): EntryFormState {
        if (next == categoryId) return this
        return copy(categoryId = next, subcategoryId = if (next == null) null else recalled)
    }

    /** Inline creation selects what it just made without disturbing anything else. */
    fun withCreatedCategory(id: Long): EntryFormState =
        copy(categoryId = id, subcategoryId = null)

    fun withCreatedSubcategory(id: Long): EntryFormState = copy(subcategoryId = id)

    /**
     * Every reason this cannot be saved, in field order, so the sheet can mark each
     * field at once instead of revealing problems one save at a time.
     */
    fun problems(): List<EntryProblem> = buildList {
        when {
            amount.isEmpty -> add(EntryProblem.AMOUNT_MISSING)
            !amount.value.isPositive -> add(EntryProblem.AMOUNT_NOT_POSITIVE)
        }
        if (type.requiresCategory && categoryId == null) add(EntryProblem.CATEGORY_MISSING)
        if (accountId == null) add(EntryProblem.ACCOUNT_MISSING)
        if (type.requiresDestination) {
            when {
                toAccountId == null -> add(EntryProblem.DESTINATION_MISSING)
                toAccountId == accountId -> add(EntryProblem.DESTINATION_SAME_AS_SOURCE)
            }
        }
    }

    val canSave: Boolean get() = problems().isEmpty()

    companion object {
        fun recording(now: LocalDateTime, accountId: Long? = null): EntryFormState =
            EntryFormState(occurredAt = now, accountId = accountId)

        fun editing(transaction: Transaction, at: LocalDateTime): EntryFormState = EntryFormState(
            id = transaction.id,
            type = transaction.type,
            amount = AmountInput.of(transaction.amount),
            occurredAt = at,
            accountId = transaction.accountId,
            toAccountId = transaction.toAccountId,
            categoryId = transaction.categoryId,
            subcategoryId = transaction.subcategoryId,
            note = transaction.note.orEmpty(),
        )
    }
}

/** The amount as stored, once [EntryFormState.problems] is empty. */
val EntryFormState.money: Money get() = amount.value
