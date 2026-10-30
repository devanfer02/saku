package com.dvnnfrr.saku.receipt

import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.sum
import org.junit.Assert.assertEquals
import org.junit.Test

class DiscountSpreadTest {

    private fun money(vararg rupiah: Long) = rupiah.map(::Money)

    @Test
    fun `the alignment page card discount spreads to the agreed amounts`() {
        val spread = spreadOverItems(money(15_500, 38_900, 72_500, 32_400, 21_900, 44_900), Money(20_000))
        assertEquals(money(14_129, 35_459, 66_087, 29_534, 19_963, 40_928), spread)
        assertEquals(Money(206_100), spread.sum())
    }

    @Test
    fun `leftover rupiah go to the largest remainders, ties to the earlier item`() {
        assertEquals(listOf(4L, 3L, 3L), spreadShares(10, listOf(1, 1, 1)))
        assertEquals(listOf(1L, 1L, 0L), spreadShares(2, listOf(5, 5, 5)))
    }

    @Test
    fun `tax and service added on top are shared the same way`() {
        val spread = spreadOverItems(money(10_000, 30_000), Money.ZERO, additions = Money(4_001))
        assertEquals(money(11_000, 33_001), spread)
    }

    @Test
    fun `shares always add up exactly`() {
        val nets = money(1, 7, 13, 99_999, 250_000, 3)
        listOf(0L, 1L, 17L, 9_999L, 123_457L).forEach { discount ->
            assertEquals(nets.sum() - Money(discount), spreadOverItems(nets, Money(discount)).sum())
        }
    }

    @Test
    fun `no positive weight shares evenly, and no items get nothing`() {
        assertEquals(listOf(2L, 1L), spreadShares(3, listOf(0, 0)))
        assertEquals(emptyList<Long>(), spreadShares(3, emptyList()))
    }
}
