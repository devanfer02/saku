package com.dvnnfrr.saku.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A ranked list's percentage column has to add up. Rounding each row on its own does
 * not, and design.md states the rule the other way round — the remainder goes to the
 * largest slices — so this pins it.
 */
class WholePercentsTest {

    private fun totals(vararg basisPoints: Int): List<CategoryTotal> =
        basisPoints.mapIndexed { index, bp ->
            CategoryTotal(
                categoryId = index.toLong(),
                name = "c$index",
                colorSlot = ColorSlot(index % ColorSlot.COUNT),
                total = Money(bp.toLong()),
                shareBasisPoints = bp,
            )
        }

    @Test
    fun `six equal slices sum to 100, not 102`() {
        val percents = wholePercents(totals(1667, 1667, 1667, 1667, 1666, 1666))

        assertEquals(100, percents.sum())
        // Per-row half-up gives 17 six times. Largest-remainder gives four 17s and
        // two 16s, which is the honest split.
        assertEquals(listOf(17, 17, 17, 17, 16, 16), percents)
    }

    @Test
    fun `three equal thirds sum to 100`() {
        val percents = wholePercents(totals(3334, 3333, 3333))

        assertEquals(100, percents.sum())
        assertEquals(listOf(34, 33, 33), percents)
    }

    @Test
    fun `the leftover goes to the largest fractional parts`() {
        val percents = wholePercents(totals(5050, 2525, 2425))

        assertEquals(100, percents.sum())
        // Floors are 50 + 25 + 24 = 99, one point short. The fractions are .50, .25
        // and .25, so the spare goes to the first — largest remainder, not largest
        // slice, though here they happen to coincide.
        assertEquals(listOf(51, 25, 24), percents)
    }

    @Test
    fun `an exact split needs no correction`() {
        assertEquals(listOf(50, 30, 20), wholePercents(totals(5000, 3000, 2000)))
    }

    @Test
    fun `an empty or all-zero list does not divide by nothing`() {
        assertEquals(emptyList<Int>(), wholePercents(emptyList()))
        assertEquals(listOf(0, 0), wholePercents(totals(0, 0)))
    }

    @Test
    fun `a single slice takes the whole hundred`() {
        assertEquals(listOf(100), wholePercents(totals(10_000)))
    }

    // sharePercent: one figure on its own, beside the list rule above.

    @Test
    fun `a share rounds half up`() {
        assertEquals(80, sharePercent(Money(8_000_000), Money(10_000_000)))
        assertEquals(25, sharePercent(Money(1_500_000), Money(6_000_000)))
        assertEquals(1, sharePercent(Money(5), Money(1_000)))
        assertEquals(0, sharePercent(Money(4), Money(1_000)))
    }

    @Test
    fun `a share of nothing is no percentage at all`() {
        assertNull(sharePercent(Money.ZERO, Money.ZERO))
        assertNull(sharePercent(Money(500), Money.ZERO))
    }

    @Test
    fun `the single-row percentage routes through sharePercent`() {
        // 0.5% reads as 1%, as it did before the rule moved.
        assertEquals(1, totals(50).single().wholePercent)
        assertEquals(0, totals(49).single().wholePercent)
        assertEquals(17, totals(1667).single().wholePercent)
    }
}
