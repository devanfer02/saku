package com.dvnnfrr.saku.ui.theme

import com.dvnnfrr.saku.domain.ThemePreference
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SakuThemeTest {

    @Test
    fun `follow system defers to the device`() {
        assertTrue(ThemePreference.FOLLOW_SYSTEM.resolveDarkTheme(systemInDark = true))
        assertFalse(ThemePreference.FOLLOW_SYSTEM.resolveDarkTheme(systemInDark = false))
    }

    @Test
    fun `an explicit preference overrides the device in both directions`() {
        assertFalse(ThemePreference.LIGHT.resolveDarkTheme(systemInDark = true))
        assertTrue(ThemePreference.DARK.resolveDarkTheme(systemInDark = false))
    }

    @Test
    fun `every preference resolves`() {
        for (preference in ThemePreference.entries) {
            preference.resolveDarkTheme(systemInDark = true)
            preference.resolveDarkTheme(systemInDark = false)
        }
    }
}
