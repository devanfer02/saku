package com.dvnnfrr.saku.ui.shell

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dvnnfrr.saku.domain.ThemePreference
import com.dvnnfrr.saku.ui.navigation.SakuDestination
import com.dvnnfrr.saku.ui.navigation.SakuNavHost
import com.dvnnfrr.saku.ui.navigation.navigateTo
import com.dvnnfrr.saku.ui.theme.SakuPreview
import com.dvnnfrr.saku.ui.theme.SakuPreviewSurface

/**
 * The frame every screen lives inside: a bottom bar over a [SakuNavHost].
 *
 * The shell owns navigation and nothing else. It reads no repository and holds no
 * ViewModel, so a feature track can replace a destination's content without
 * touching this file.
 */
@Composable
fun SakuShell(
    modifier: Modifier = Modifier,
    appearance: ThemePreference = ThemePreference.FOLLOW_SYSTEM,
    onAppearanceChange: (ThemePreference) -> Unit = {},
    /** Open Trans with the entry sheet on a new transaction; see [SakuNavHost]. */
    openEntry: Boolean = false,
    onEntryOpened: () -> Unit = {},
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val current = SakuDestination.owning(backStackEntry?.destination?.route)

    // Switch to Trans first; Trans itself opens the sheet and consumes the request.
    LaunchedEffect(openEntry) {
        if (openEntry) navController.navigateTo(SakuDestination.TRANSACTIONS)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                SakuDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = destination == current,
                        onClick = {
                            // Tapping the lit tab returns it to its root, so a screen
                            // pushed from More is never more than one tap from the menu.
                            if (destination == current) {
                                navController.popBackStack(destination.route, inclusive = false)
                            } else {
                                navController.navigateTo(destination)
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(stringResource(destination.labelRes)) },
                        modifier = Modifier.testTag(destination.navTestTag),
                    )
                }
            }
        },
    ) { contentPadding ->
        SakuNavHost(
            navController = navController,
            appearance = appearance,
            onAppearanceChange = onAppearanceChange,
            openEntry = openEntry,
            onEntryOpened = onEntryOpened,
            modifier = Modifier.padding(contentPadding),
        )
    }
}

@SakuPreview
@Composable
private fun SakuShellPreview() {
    SakuPreviewSurface(padded = false) { SakuShell() }
}
