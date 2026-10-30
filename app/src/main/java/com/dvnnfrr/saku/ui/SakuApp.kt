package com.dvnnfrr.saku.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dvnnfrr.saku.data.AppearanceStore
import com.dvnnfrr.saku.domain.ThemePreference
import com.dvnnfrr.saku.ui.firstrun.FirstRunGate
import com.dvnnfrr.saku.ui.firstrun.FirstRunGateViewModel
import com.dvnnfrr.saku.ui.navigation.FirstRunNavHost
import com.dvnnfrr.saku.ui.shell.SakuShell
import com.dvnnfrr.saku.ui.theme.SakuTheme

/**
 * Root of the app.
 *
 * The appearance preference is seeded synchronously from [AppearanceStore.current] so
 * the first frame paints in the chosen theme. Collecting the flow without an initial
 * value would flash the system theme for one frame on every cold start: a light
 * flash on a dark-mode device, which is exactly the moment it is most visible.
 *
 * First-run setup is decided here, before the shell exists: on an unused install the
 * welcome flow is shown in place of the shell, with no bottom bar, until it closes.
 */
@Composable
fun SakuApp(
    store: AppearanceStore,
    openEntry: Boolean = false,
    onEntryOpened: () -> Unit = {},
    firstRunViewModel: FirstRunGateViewModel = viewModel(factory = FirstRunGateViewModel.Factory),
) {
    val preference by store.preference.collectAsState(initial = store.current())
    val firstRun by firstRunViewModel.gate.collectAsState()

    SakuApp(
        preference = preference,
        onAppearanceChange = store::set,
        openEntry = openEntry,
        onEntryOpened = onEntryOpened,
        firstRun = firstRun,
        onFirstRunSkip = firstRunViewModel::skip,
        onFirstRunComplete = firstRunViewModel::complete,
        onFirstRunImportLeft = firstRunViewModel::onImportLeft,
    )
}

@Composable
fun SakuApp(
    preference: ThemePreference = ThemePreference.FOLLOW_SYSTEM,
    onAppearanceChange: (ThemePreference) -> Unit = {},
    /** The weekly reminder was tapped: open Trans with the entry sheet on a new transaction. */
    openEntry: Boolean = false,
    onEntryOpened: () -> Unit = {},
    firstRun: FirstRunGate = FirstRunGate.CLOSED,
    onFirstRunSkip: () -> Unit = {},
    onFirstRunComplete: () -> Unit = {},
    onFirstRunImportLeft: () -> Unit = {},
) {
    SakuTheme(preference) {
        val page = Modifier
            .fillMaxSize()
            .background(SakuTheme.colors.background)

        when (firstRun) {
            // One database read. The page colour, not a spinner: it lasts a frame or two.
            FirstRunGate.CHECKING -> Box(page)
            // No Scaffold here, so the insets the shell's Scaffold would apply are ours.
            FirstRunGate.OFFER -> FirstRunNavHost(
                onImportLeft = onFirstRunImportLeft,
                onComplete = onFirstRunComplete,
                onSkip = onFirstRunSkip,
                modifier = page.safeDrawingPadding(),
            )
            FirstRunGate.CLOSED -> SakuShell(
                modifier = page,
                appearance = preference,
                onAppearanceChange = onAppearanceChange,
                openEntry = openEntry,
                onEntryOpened = onEntryOpened,
            )
        }
    }
}
