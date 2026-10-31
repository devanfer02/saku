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
import com.dvnnfrr.saku.R
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.MaterialTheme
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
                        label = { NavLabel(destination) },
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

/**
 * A bottom-bar label. It stops growing at 1.3x font scale and stays on one line, so
 * "Accounts" at 200% does not break mid-word. TalkBack reads the full name ("Trans."
 * is spoken as "Transactions").
 */
@Composable
private fun NavLabel(destination: SakuDestination) {
    val scale = LocalDensity.current.fontScale
    val base = MaterialTheme.typography.labelMedium.fontSize
    val size = if (scale > NAV_LABEL_MAX_SCALE) base * (NAV_LABEL_MAX_SCALE / scale) else base
    val spoken = if (destination == SakuDestination.TRANSACTIONS) stringResource(R.string.nav_transactions_spoken) else null
    Text(
        text = stringResource(destination.labelRes),
        fontSize = size,
        maxLines = 1,
        softWrap = false,
        modifier = if (spoken != null) Modifier.semantics { contentDescription = spoken } else Modifier,
    )
}

private const val NAV_LABEL_MAX_SCALE = 1.3f
