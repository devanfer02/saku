package com.dvnnfrr.saku.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.ui.graphics.vector.ImageVector
import com.dvnnfrr.saku.R

/**
 * The four bottom-navigation destinations. Every feature track mounts into one of
 * these, so the set is closed: a new screen is a route *within* a destination, not
 * a fifth tab.
 */
enum class SakuDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    TRANSACTIONS("transactions", R.string.nav_transactions, Icons.AutoMirrored.Filled.ReceiptLong),
    STATS("stats", R.string.nav_stats, Icons.Filled.BarChart),
    ACCOUNTS("accounts", R.string.nav_accounts, Icons.Filled.AccountBalanceWallet),
    MORE("more", R.string.nav_more, Icons.Filled.MoreHoriz),
    ;

    /** Test tag on the navigation item, so tests do not depend on the visible label. */
    val navTestTag: String get() = "nav_$route"

    companion object {
        val START = TRANSACTIONS

        fun fromRoute(route: String?): SakuDestination? = entries.firstOrNull { it.route == route }

        /**
         * The tab that stays lit for [route], including the screens pushed from the
         * More menu. Without this a pushed screen matches no tab, and the bar falls
         * back to the start destination — so opening Import from More would light
         * Transactions, which reads as the app having navigated somewhere else.
         */
        fun owning(route: String?): SakuDestination = when {
            fromRoute(route) != null -> fromRoute(route)!!
            MoreRoute.fromRoute(route) != null -> MORE
            else -> START
        }
    }
}
