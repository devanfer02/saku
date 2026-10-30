package com.dvnnfrr.saku.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dvnnfrr.saku.ui.firstrun.FirstRunSetupScreen
import com.dvnnfrr.saku.ui.firstrun.SakuImportChooser
import com.dvnnfrr.saku.ui.firstrun.WelcomeScreen
import com.dvnnfrr.saku.ui.importexport.DatabaseImportScreen
import com.dvnnfrr.saku.ui.importexport.ImportEntryPoint
import com.dvnnfrr.saku.ui.importexport.ImportScreen
import com.dvnnfrr.saku.ui.importexport.RestoreScreen

/**
 * First-run setup's own graph: the welcome screen, its three paths, and the existing
 * import screens mounted as they are.
 *
 * Leaving an import route by any means (its back arrow, system back, or a result's
 * Continue) calls [onImportLeft], which asks the ledger whether that path finished.
 * Continue pops back to the welcome route on purpose: that is a leave like any other, so
 * rows in the ledger complete first run and the shell opens on its start tab, Trans.
 * Watching the back stack rather than wiring each callback is what catches system back,
 * which the restore and database-import screens do not report.
 */
@Composable
fun FirstRunNavHost(
    onImportLeft: () -> Unit,
    onComplete: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val back: () -> Unit = { navController.popBackStack() }
    val toWelcome: () -> Unit = {
        navController.popBackStack(FirstRunRoute.WELCOME.route, inclusive = false)
    }
    val importLeft by rememberUpdatedState(onImportLeft)

    LaunchedEffect(navController) {
        var previous: FirstRunRoute? = null
        navController.currentBackStackEntryFlow.collect { entry ->
            val current = FirstRunRoute.fromRoute(entry.destination.route)
            val leftAnImport = previous?.isImport == true && current?.isImport != true
            if (leftAnImport) importLeft()
            previous = current
        }
    }

    NavHost(
        navController = navController,
        startDestination = FirstRunRoute.START.route,
        modifier = modifier,
    ) {
        composable(FirstRunRoute.WELCOME.route) {
            WelcomeScreen(
                onImportFromSaku = { navController.navigate(FirstRunRoute.FROM_SAKU.route) },
                onImportFromMoneyManager = {
                    navController.navigate(FirstRunRoute.IMPORT_MONEY_MANAGER.route)
                },
                onSetUpManually = { navController.navigate(FirstRunRoute.MANUAL.route) },
                onSkip = onSkip,
            )
        }
        composable(FirstRunRoute.FROM_SAKU.route) {
            SakuImportChooser(
                onBack = back,
                onOpen = { entry -> FirstRunRoute.of(entry)?.let { navController.navigate(it.route) } },
            )
        }
        composable(FirstRunRoute.MANUAL.route) {
            FirstRunSetupScreen(onBack = back, onFinished = onComplete)
        }
        composable(FirstRunRoute.IMPORT_MONEY_MANAGER.route) {
            ImportScreen(
                entryPoint = ImportEntryPoint.MONEY_MANAGER,
                onBack = back,
                // Each of these follows a committed import; the ledger check decides.
                onOpenHistory = toWelcome,
                onContinue = toWelcome,
            )
        }
        composable(FirstRunRoute.IMPORT_EXCEL.route) {
            ImportScreen(
                entryPoint = ImportEntryPoint.EXCEL,
                onBack = back,
                onOpenHistory = toWelcome,
                onContinue = toWelcome,
            )
        }
        composable(FirstRunRoute.RESTORE.route) {
            RestoreScreen(onBack = back, onContinue = toWelcome)
        }
        composable(FirstRunRoute.IMPORT_DATABASE.route) {
            DatabaseImportScreen(onBack = back, onContinue = toWelcome)
        }
    }
}
