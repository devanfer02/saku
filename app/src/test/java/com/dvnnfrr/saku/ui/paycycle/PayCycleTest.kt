package com.dvnnfrr.saku.ui.paycycle

import androidx.test.core.app.ApplicationProvider
import com.dvnnfrr.saku.data.PeriodStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PayCycleTest {

    private val zone = TimeZone.of("Asia/Jakarta")
    private val dispatcher = StandardTestDispatcher()
    private val clock = object : Clock {
        override fun now(): Instant = LocalDateTime(2026, 10, 30, 9, 0).toInstant(zone)
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `setting the 30th on 30 Oct 2026 previews the spec's two pay months`() {
        val state = assemblePayCycle(startDay = 30, today = LocalDate(2026, 10, 30))

        assertEquals("30 Oct to 29 Nov 2026", state.current?.span)
        assertEquals("November 2026", state.current?.label)
        assertEquals("30 Sep to 29 Oct 2026", state.previous?.span)
        assertEquals("October 2026", state.previous?.label)
    }

    @Test
    fun `no start day previews nothing`() {
        val state = assemblePayCycle(startDay = null, today = LocalDate(2026, 10, 30))

        assertNull(state.current)
        assertNull(state.previous)
    }

    @Test
    fun `picking and clearing a day goes through the shared store`() = runTest(dispatcher) {
        val store = PeriodStore(ApplicationProvider.getApplicationContext())
        val vm = PayCycleViewModel(store, zone, clock)

        vm.selectStartDay(25)
        assertEquals(25, store.current().startDay)
        assertEquals("25 Oct to 24 Nov 2026", vm.state.first { it.startDay == 25 }.current?.span)

        vm.clear()
        assertNull(store.current().startDay)
        assertNull(vm.state.first { it.startDay == null }.current)
    }
}
