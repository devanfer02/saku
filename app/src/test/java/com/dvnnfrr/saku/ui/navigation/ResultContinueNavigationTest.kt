package com.dvnnfrr.saku.ui.navigation

import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Where Continue on a result screen lands in the More stack: an import, restore or
 * database import goes to Trans, backup and export go back to the More menu, and either
 * way the More tab reopens on its menu rather than on the finished result.
 *
 * The graph mirrors [SakuNavHost]'s routes with empty screens, so only the back stack
 * is under test. First run's Continue is a pop to the welcome route; the ledger check it
 * triggers is covered by the gate view model's tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ResultContinueNavigationTest {

    private fun shell(): NavHostController {
        val nav = NavHostController(ApplicationProvider.getApplicationContext())
        nav.navigatorProvider.addNavigator(ComposeNavigator())
        nav.graph = nav.createGraph(startDestination = SakuDestination.START.route) {
            SakuDestination.entries.forEach { composable(it.route) {} }
            MoreRoute.entries.forEach { composable(it.route) {} }
        }
        return nav
    }

    private fun NavHostController.openFromMore(route: MoreRoute) {
        navigateTo(SakuDestination.MORE)
        navigate(route.route)
    }

    private val NavHostController.route: String?
        get() = currentDestination?.route

    @Test
    fun `Continue after an import lands on Trans`() {
        val nav = shell()
        listOf(MoreRoute.IMPORT_MONEY_MANAGER, MoreRoute.IMPORT_EXCEL, MoreRoute.RESTORE, MoreRoute.IMPORT_DATABASE)
            .forEach { route ->
                nav.openFromMore(route)

                nav.continueToLedger()

                assertEquals("Continue from $route", SakuDestination.TRANSACTIONS.route, nav.route)
            }
    }

    @Test
    fun `More reopens on its menu after Continue to Trans`() {
        val nav = shell()
        nav.openFromMore(MoreRoute.RESTORE)
        nav.continueToLedger()

        nav.navigateTo(SakuDestination.MORE)

        assertEquals(SakuDestination.MORE.route, nav.route)
    }

    @Test
    fun `Continue after a backup or an export returns to the More menu`() {
        val nav = shell()
        listOf(MoreRoute.BACKUP, MoreRoute.EXPORT_EXCEL).forEach { route ->
            nav.openFromMore(route)

            nav.popToMoreMenu()

            assertEquals("Continue from $route", SakuDestination.MORE.route, nav.route)
        }
    }
}
