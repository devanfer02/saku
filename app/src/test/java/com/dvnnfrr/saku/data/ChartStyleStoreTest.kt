package com.dvnnfrr.saku.data

import androidx.test.core.app.ApplicationProvider
import com.dvnnfrr.saku.domain.ChartStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChartStyleStoreTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun store() = ChartStyleStore(context)

    @Test
    fun `defaults to ranked bars`() {
        assertEquals(ChartStyle.BARS, store().current())
    }

    @Test
    fun `a choice survives a new store instance`() = runTest {
        store().set(ChartStyle.PIE)

        assertEquals(ChartStyle.PIE, store().current())
        assertEquals(ChartStyle.PIE, store().style.first())
    }

    @Test
    fun `an unrecognised stored value falls back to bars`() {
        context.getSharedPreferences("saku_stats", android.content.Context.MODE_PRIVATE)
            .edit()
            .putString("chart_style", "TREEMAP")
            .commit()

        assertEquals(ChartStyle.BARS, store().current())
    }
}
