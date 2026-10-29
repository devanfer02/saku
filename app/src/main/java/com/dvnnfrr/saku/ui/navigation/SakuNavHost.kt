package com.dvnnfrr.saku.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.dvnnfrr.saku.domain.ThemePreference
import com.dvnnfrr.saku.ui.accounts.AccountsScreen
import com.dvnnfrr.saku.ui.categories.CategoriesScreen
import com.dvnnfrr.saku.ui.importexport.BackupScreen
import com.dvnnfrr.saku.ui.importexport.ExportScreen
import com.dvnnfrr.saku.ui.importexport.ImportEntryPoint
import com.dvnnfrr.saku.ui.importexport.ImportHistoryScreen
import com.dvnnfrr.saku.ui.importexport.ImportScreen
import com.dvnnfrr.saku.ui.importexport.RestoreScreen
import com.dvnnfrr.saku.ui.shell.MoreScreen
import com.dvnnfrr.saku.ui.stats.StatsScreen
import com.dvnnfrr.saku.ui.transactions.TransactionsScreen

/**
 * One entry per destination: the four tabs, then the screens the More menu pushes.
 */
@Composable
fun SakuNavHost(
    navController: NavHostController,
    appearance: ThemePreference,
    onAppearanceChange: (ThemePreference) -> Unit,
    modifier: Modifier = Modifier,
) {
    val back: () -> Unit = { navController.popBackStack() }

    NavHost(
        navController = navController,
        startDestination = SakuDestination.START.route,
        modifier = modifier,
    ) {
        composable(SakuDestination.TRANSACTIONS.route) { TransactionsScreen() }
        composable(SakuDestination.STATS.route) { StatsScreen() }
        composable(SakuDestination.ACCOUNTS.route) { AccountsScreen() }
        composable(SakuDestination.MORE.route) {
            MoreScreen(
                appearance = appearance,
                onAppearanceChange = onAppearanceChange,
                // Appearance is a dialog the menu owns, so it never reaches here.
                // Anything else without a route is a track not yet wired, and doing
                // nothing beats navigating to a blank screen.
                onOpen = { entry ->
                    MoreRoute.of(entry)?.let { navController.navigate(it.route) }
                },
            )
        }

        composable(MoreRoute.CATEGORIES.route) { CategoriesScreen(onBack = back) }
        composable(MoreRoute.IMPORT_MONEY_MANAGER.route) {
            ImportScreen(
                entryPoint = ImportEntryPoint.MONEY_MANAGER,
                onBack = back,
                onViewTransactions = { navController.leaveMoreFor(SakuDestination.TRANSACTIONS) },
                onOpenHistory = { navController.replaceWithinMore(MoreRoute.IMPORT_HISTORY) },
                onDone = { navController.popToMoreMenu() },
            )
        }
        composable(MoreRoute.IMPORT_EXCEL.route) {
            ImportScreen(
                entryPoint = ImportEntryPoint.EXCEL,
                onBack = back,
                onViewTransactions = { navController.leaveMoreFor(SakuDestination.TRANSACTIONS) },
                onOpenHistory = { navController.replaceWithinMore(MoreRoute.IMPORT_HISTORY) },
                onDone = { navController.popToMoreMenu() },
            )
        }
        composable(MoreRoute.EXPORT_EXCEL.route) { ExportScreen() }
        composable(MoreRoute.IMPORT_HISTORY.route) { ImportHistoryScreen() }
        composable(MoreRoute.BACKUP.route) { BackupScreen() }
        composable(MoreRoute.RESTORE.route) { RestoreScreen() }
    }
}

/**
 * Switch tabs the way a bottom bar is expected to behave: the back stack does not
 * grow, and each destination is restored as the user left it.
 *
 * `saveState`/`restoreState` are what make that true. Without them Compose disposes
 * the outgoing destination and the next visit rebuilds it from scratch — the user
 * loses their scroll position and their selected sub-tab, which the spec forbids.
 */
fun NavHostController.navigateTo(destination: SakuDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Back to the More menu, dropping whatever it pushed. A pushed screen left on the stack
 * is saved with the tab and restored on the next visit, which is how the More tab kept
 * reopening on a finished import.
 */
fun NavHostController.popToMoreMenu() {
    popBackStack(SakuDestination.MORE.route, inclusive = false)
}

/** Switches tab from a More screen, leaving the More tab on its menu for next time. */
fun NavHostController.leaveMoreFor(destination: SakuDestination) {
    popToMoreMenu()
    navigateTo(destination)
}

/** Swaps the open More screen for [route], so back from it returns to the menu. */
fun NavHostController.replaceWithinMore(route: MoreRoute) {
    navigate(route.route) {
        popUpTo(SakuDestination.MORE.route)
        launchSingleTop = true
    }
}
