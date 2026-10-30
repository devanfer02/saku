package com.dvnnfrr.saku.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.dvnnfrr.saku.data.AppearanceStore
import com.dvnnfrr.saku.domain.ThemePreference
import com.dvnnfrr.saku.ui.shell.SakuShell
import com.dvnnfrr.saku.ui.theme.SakuTheme

/**
 * Root of the app.
 *
 * The appearance preference is seeded synchronously from [AppearanceStore.current] so
 * the first frame paints in the chosen theme. Collecting the flow without an initial
 * value would flash the system theme for one frame on every cold start — a light
 * flash on a dark-mode device, which is exactly the moment it is most visible.
 */
@Composable
fun SakuApp(
    store: AppearanceStore,
    openEntry: Boolean = false,
    onEntryOpened: () -> Unit = {},
) {
    val preference by store.preference.collectAsState(initial = store.current())

    SakuApp(
        preference = preference,
        onAppearanceChange = store::set,
        openEntry = openEntry,
        onEntryOpened = onEntryOpened,
    )
}

@Composable
fun SakuApp(
    preference: ThemePreference = ThemePreference.FOLLOW_SYSTEM,
    onAppearanceChange: (ThemePreference) -> Unit = {},
    /** The weekly reminder was tapped: open Trans with the entry sheet on a new transaction. */
    openEntry: Boolean = false,
    onEntryOpened: () -> Unit = {},
) {
    SakuTheme(preference) {
        SakuShell(
            modifier = Modifier
                .fillMaxSize()
                .background(SakuTheme.colors.background),
            appearance = preference,
            onAppearanceChange = onAppearanceChange,
            openEntry = openEntry,
            onEntryOpened = onEntryOpened,
        )
    }
}
