package com.dvnnfrr.saku.ui.shell

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dvnnfrr.saku.ui.navigation.SakuDestination
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The shell's one job that is easy to get wrong: leaving a destination and coming
 * back must not reset it. Navigation Compose only does that when the navigate call
 * carries saveState/restoreState, and dropping either is a silent regression — the
 * app still works, it just quietly forgets where the user was.
 *
 * Requires a device or emulator. CI runs the equivalent assertion through the
 * Maestro smoke flow, which does have one.
 */
@RunWith(AndroidJUnit4::class)
class SakuShellNavigationTest {

    @get:Rule
    val rule = createComposeRule()

    private fun launchShell() {
        rule.setContent { MaterialTheme { SakuShell() } }
    }

    @Test
    fun the_transactions_destination_is_shown_first() {
        launchShell()

        rule.onNodeWithText("Daily").assertIsSelected()
    }

    @Test
    fun a_destination_keeps_its_selected_tab_across_navigation() {
        launchShell()

        rule.onNodeWithText("Calendar").performClick()
        rule.onNodeWithText("Calendar").assertIsSelected()

        rule.onNodeWithTag(SakuDestination.STATS.navTestTag).performClick()
        rule.onNodeWithTag(SakuDestination.ACCOUNTS.navTestTag).performClick()
        rule.onNodeWithTag(SakuDestination.TRANSACTIONS.navTestTag).performClick()

        // Would read Daily if saveState/restoreState were dropped from navigateTo.
        rule.onNodeWithText("Calendar").assertIsSelected()
    }

    @Test
    fun every_destination_mounts() {
        launchShell()

        SakuDestination.entries.forEach { destination ->
            rule.onNodeWithTag(destination.navTestTag).performClick()
            rule.onNodeWithTag(destination.navTestTag).assertIsSelected()
        }
    }

    @Test
    fun the_more_menu_lists_every_entry() {
        launchShell()

        rule.onNodeWithTag(SakuDestination.MORE.navTestTag).performClick()

        listOf(
            "Categories",
            "Import from Money Manager",
            "Import Excel",
            "Export Excel",
            "Import history",
            "Appearance",
        ).forEach { label ->
            rule.onNodeWithText(label).assertExists()
        }
    }
}
