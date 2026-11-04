package com.dvnnfrr.saku.ui.entry

import com.dvnnfrr.saku.data.repository.AccountRepository
import com.dvnnfrr.saku.domain.AccountType
import kotlinx.datetime.Instant

/**
 * An account made by name from a picker (entry sheet, Receipt screen): a Bank account with
 * no opening balance, opened at the epoch like the seeded ones, so every past row counts.
 * Type, colour and balance are set later on the Accounts screen. An existing name, archived
 * or not, is reused rather than duplicated.
 */
internal suspend fun quickAccount(accounts: AccountRepository, name: String): Long =
    accounts.findOrCreate(name.trim(), AccountType.BANK, Instant.fromEpochSeconds(0))
