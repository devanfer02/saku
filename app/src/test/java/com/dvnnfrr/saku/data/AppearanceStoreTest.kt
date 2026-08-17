package com.dvnnfrr.saku.data

import androidx.test.core.app.ApplicationProvider
import com.dvnnfrr.saku.domain.ThemePreference
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppearanceStoreTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun store() = SharedPreferencesAppearanceStore(context)

    @Test
    fun `defaults to following the system`() {
        assertEquals(ThemePreference.FOLLOW_SYSTEM, store().current())
    }

    @Test
    fun `a choice survives a new store instance`() {
        store().set(ThemePreference.DARK)

        assertEquals(ThemePreference.DARK, store().current())
    }

    @Test
    fun `the flow opens on the stored value`() = runTest {
        store().set(ThemePreference.LIGHT)

        assertEquals(ThemePreference.LIGHT, store().preference.first())
    }

    @Test
    fun `an unrecognised stored value falls back rather than throwing`() {
        context.getSharedPreferences("saku_appearance", android.content.Context.MODE_PRIVATE)
            .edit()
            .putString("theme_preference", "SEPIA")
            .commit()

        // A downgrade after a future release added a mode would otherwise crash on
        // launch, leaving no way to reach the setting that caused it.
        assertEquals(ThemePreference.FOLLOW_SYSTEM, store().current())
    }
}
