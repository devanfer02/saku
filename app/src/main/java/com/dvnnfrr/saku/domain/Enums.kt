package com.dvnnfrr.saku.domain

/**
 * What a transaction does to money.
 *
 * Only [EXPENSE] and [INCOME] are flows of money in or out of the owner's control.
 * A [TRANSFER] moves money between the owner's own accounts and an [ADJUSTMENT]
 * corrects a balance that drifted — neither is spending, so both are excluded from
 * every income and expense total in the app.
 */
enum class TxnType {
    EXPENSE,
    INCOME,
    TRANSFER,
    ADJUSTMENT,
    ;

    /** True only for the two types that belong in income and expense totals. */
    val countsAsFlow: Boolean get() = this == EXPENSE || this == INCOME

    /** True for the types that require a category. */
    val requiresCategory: Boolean get() = countsAsFlow

    /** True for the type that requires a destination account. */
    val requiresDestination: Boolean get() = this == TRANSFER
}

enum class AccountType {
    CASH,
    BANK,
    INVESTMENT,
    LIABILITY,
    ;

    val group: AccountGroup
        get() = when (this) {
            CASH, BANK -> AccountGroup.SPENDABLE
            INVESTMENT -> AccountGroup.INVESTED
            LIABILITY -> AccountGroup.OWED
        }
}

/**
 * How the accounts screen groups holdings. The header reads Spendable · Invested ·
 * Total, and the [OWED] row is hidden entirely while no liability account exists.
 */
enum class AccountGroup {
    SPENDABLE,
    INVESTED,
    OWED,
}

enum class CategoryKind {
    EXPENSE,
    INCOME,
}

/** Which theme the user has pinned, or neither. */
enum class ThemePreference {
    FOLLOW_SYSTEM,
    LIGHT,
    DARK,
}
