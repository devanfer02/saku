package com.dvnnfrr.saku.ui.stats

import com.dvnnfrr.saku.domain.CategoryTotal
import com.dvnnfrr.saku.domain.ColorSlot
import com.dvnnfrr.saku.domain.DateRange
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.PeriodMode
import com.dvnnfrr.saku.domain.Subcategory
import com.dvnnfrr.saku.domain.Transaction
import com.dvnnfrr.saku.domain.TxnType
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ranking, the cap and the shares, folded without a database.
 *
 * These are the rules the spec states, and every one of them is decided by a pure
 * function so it can be asserted against a plain list rather than through a ViewModel
 * and an in-memory Room.
 */
class StatsAssemblyTest {

    /** Basis points as the repository computes them: exact integer ratio of the sum. */
    private fun totals(vararg rows: Pair<String, Long>): List<CategoryTotal> {
        val sum = rows.sumOf { it.second }
        return rows.mapIndexed { index, (name, amount) ->
            CategoryTotal(
                categoryId = index + 1L,
                name = name,
                colorSlot = ColorSlot(index % ColorSlot.COUNT),
                total = Money(amount),
                shareBasisPoints = if (sum == 0L) 0 else ((amount * 10_000L) / sum).toInt(),
            )
        }
    }

    // Requirement: rank categories by amount with share and value.

    @Test
    fun `the ranked list for a month orders by amount and carries share and value`() {
        val composition = rankComposition(
            totals(
                "Perawatan Diri" to 1_791_900,
                "Makan & Minum" to 1_257_700,
                "Pemberian" to 1_050_000,
                "Jajan & Hiburan" to 843_800,
                "Obat" to 80_000,
                "Kebutuhan Kos" to 38_000,
            ),
        )

        assertEquals(
            listOf(
                "Perawatan Diri",
                "Makan & Minum",
                "Pemberian",
                "Jajan & Hiburan",
                "Obat",
                "Kebutuhan Kos",
            ),
            composition.rows.map { it.name },
        )
        assertEquals(
            listOf(1_791_900L, 1_257_700L, 1_050_000L, 843_800L, 80_000L, 38_000L),
            composition.rows.map { it.total.rupiah },
        )
        assertEquals(Money(5_061_400), composition.total)
        assertEquals(100, composition.rows.sumOf { it.percent })
        // 1.791.900 of 5.061.400 is 35.4%.
        assertEquals(35, composition.rows[0].percent)
    }

    @Test
    fun `an unsorted input is ranked rather than trusted`() {
        val composition = rankComposition(
            totals("Obat" to 80_000, "Pemberian" to 1_050_000, "Makan & Minum" to 1_257_700),
        )

        assertEquals(
            listOf("Makan & Minum", "Pemberian", "Obat"),
            composition.rows.map { it.name },
        )
    }

    @Test
    fun `six equal slices still sum to exactly one hundred`() {
        // Rounding each row on its own gives 17% six times, which reads 102%.
        val composition = rankComposition(
            totals(
                "A" to 100_000, "B" to 100_000, "C" to 100_000,
                "D" to 100_000, "E" to 100_000, "F" to 100_000,
            ),
        )

        assertEquals(100, composition.rows.sumOf { it.percent })
    }

    // Requirement: chart category composition — the eight-slot cap.

    @Test
    fun `more than eight categories charts the largest eight and combines the rest`() {
        val composition = rankComposition(
            totals(
                "A" to 900_000, "B" to 800_000, "C" to 700_000, "D" to 600_000,
                "E" to 500_000, "F" to 400_000, "G" to 300_000, "H" to 200_000,
                "I" to 100_000, "J" to 50_000, "K" to 25_000,
            ),
        )

        assertEquals(9, composition.rows.size)
        assertEquals(
            listOf("A", "B", "C", "D", "E", "F", "G", "H"),
            composition.rows.dropLast(1).map { it.name },
        )

        val other = composition.rows.last()
        assertTrue(other.isOther)
        assertNull(other.name)
        assertNull(other.colorSlot)
        assertEquals(Money(175_000), other.total)
        assertEquals(100, composition.rows.sumOf { it.percent })
    }

    @Test
    fun `exactly eight categories are all charted with no Other row`() {
        val composition = rankComposition(
            totals(
                "A" to 8, "B" to 7, "C" to 6, "D" to 5,
                "E" to 4, "F" to 3, "G" to 2, "H" to 1,
            ),
        )

        assertEquals(8, composition.rows.size)
        assertNull(composition.other)
        assertTrue(composition.otherMembers.isEmpty())
    }

    @Test
    fun `Other keeps its constituents with their own figures for the drill`() {
        val composition = rankComposition(
            totals(
                "A" to 900_000, "B" to 800_000, "C" to 700_000, "D" to 600_000,
                "E" to 500_000, "F" to 400_000, "G" to 300_000, "H" to 200_000,
                "Paket" to 100_000, "Parkir" to 50_000,
            ),
        )

        assertEquals(listOf("Paket", "Parkir"), composition.otherMembers.map { it.name })
        assertEquals(
            listOf(100_000L, 50_000L),
            composition.otherMembers.map { it.total.rupiah },
        )
        // The members' shares are the ones the collapsed view accounted for, so
        // expanding Other cannot contradict the row it came from.
        assertEquals(
            composition.other!!.percent,
            composition.otherMembers.sumOf { it.percent },
        )
    }

    // Requirement: colour follows the category, not its rank.

    @Test
    fun `a category keeps its colour when it moves down the ranking`() {
        val august = totals("Pemberian" to 1_000_000, "Makan & Minum" to 500_000)
        val pemberianSlot = august.first { it.name == "Pemberian" }.colorSlot

        val september = rankComposition(
            listOf(
                august.first { it.name == "Makan & Minum" }.copy(total = Money(900_000)),
                august.first { it.name == "Pemberian" }.copy(total = Money(100_000)),
            ),
        )

        assertEquals("Makan & Minum", september.rows[0].name)
        assertEquals("Pemberian", september.rows[1].name)
        assertEquals(pemberianSlot, september.rows[1].colorSlot)
    }

    // Requirement: a period with no data.

    @Test
    fun `an empty period composes to nothing rather than dividing by zero`() {
        val composition = rankComposition(emptyList())

        assertTrue(composition.isEmpty)
        assertEquals(Money.ZERO, composition.total)
        assertNull(composition.other)
    }

    @Test
    fun `an all-zero period yields zero shares rather than throwing`() {
        val composition = rankComposition(totals("A" to 0, "B" to 0))

        assertEquals(listOf(0, 0), composition.rows.map { it.percent })
        assertEquals(Money.ZERO, composition.total)
    }

    @Test
    fun `donut segments carry the Other label supplied by the screen`() {
        val composition = rankComposition(
            totals(
                "A" to 9, "B" to 8, "C" to 7, "D" to 6, "E" to 5,
                "F" to 4, "G" to 3, "H" to 2, "I" to 1,
            ),
        )

        val segments = donutSegments(composition.rows, otherLabel = "Other")

        assertEquals(9, segments.size)
        assertEquals("Other", segments.last().label)
        assertNull(segments.last().slot)
    }

    // Subcategory drill-down.

    private fun txn(id: Long, amount: Long, subcategoryId: Long?) = Transaction(
        id = id,
        occurredAt = Instant.fromEpochSeconds(1_760_000_000 + id),
        type = TxnType.EXPENSE,
        amount = Money(amount),
        accountId = 1,
        categoryId = 7,
        subcategoryId = subcategoryId,
        createdAt = Instant.fromEpochSeconds(1_760_000_000),
    )

    private val subcategories = listOf(
        Subcategory(1, 7, "Rutin keluarga", 0, archived = false),
        Subcategory(2, 7, "Sekali jalan", 1, archived = false),
    )

    @Test
    fun `subcategories rank by amount with shares summing to one hundred`() {
        val slices = foldSubcategories(
            transactions = listOf(
                txn(1, 300_000, subcategoryId = 2),
                txn(2, 800_000, subcategoryId = 1),
                txn(3, 200_000, subcategoryId = 1),
            ),
            subcategories = subcategories,
            slot = ColorSlot(2),
        )

        assertEquals(listOf("Rutin keluarga", "Sekali jalan"), slices.map { it.name })
        assertEquals(listOf(1_000_000L, 300_000L), slices.map { it.total.rupiah })
        assertEquals(100, slices.sumOf { it.percent })
        assertEquals(77, slices[0].percent)
    }

    @Test
    fun `untagged rows collect into a visible unassigned bucket`() {
        // The finding this screen exists for: the export left subcategory blank on all
        // 1,200 rows, so a fold that dropped them would report a category as fully split.
        val slices = foldSubcategories(
            transactions = listOf(
                txn(1, 800_000, subcategoryId = null),
                txn(2, 200_000, subcategoryId = 1),
            ),
            subcategories = subcategories,
            slot = ColorSlot(2),
        )

        assertEquals(2, slices.size)
        assertTrue(slices[0].isUnassigned)
        assertNull(slices[0].name)
        assertEquals(Money(800_000), slices[0].total)
        assertEquals(80, slices[0].percent)
        assertEquals(100, slices.sumOf { it.percent })
    }

    @Test
    fun `an archived subcategory keeps its name on the history that references it`() {
        val slices = foldSubcategories(
            transactions = listOf(txn(1, 100_000, subcategoryId = 3)),
            subcategories = subcategories + Subcategory(3, 7, "Lama", 2, archived = true),
            slot = ColorSlot(2),
        )

        assertEquals("Lama", slices.single().name)
    }

    @Test
    fun `a category with no transactions folds to no slices`() {
        assertTrue(foldSubcategories(emptyList(), subcategories, ColorSlot(2)).isEmpty())
    }

    // Comparison against the previous equivalent period.

    @Test
    fun `a percentage change is null when there is no baseline to divide by`() {
        assertNull(percentChange(Money(5_000_000), Money.ZERO))
    }

    @Test
    fun `a percentage change rounds half away from zero in the direction it moved`() {
        assertEquals(-4, percentChange(Money(5_061_400), Money(5_270_000)))
        assertEquals(12, percentChange(Money(1_120_000), Money(1_000_000)))
        assertEquals(0, percentChange(Money(1_000_000), Money(1_000_000)))
        // 1.005 against 1.000 is +0.5%, which must read as 1% rather than vanish.
        assertEquals(1, percentChange(Money(1_005), Money(1_000)))
        assertEquals(-1, percentChange(Money(995), Money(1_000)))
    }

    @Test
    fun `an empty previous period reports no baseline rather than a zero one`() {
        val comparison = PeriodComparison(
            previousRange = DateRange.ofMonth(LocalDate(2026, 7, 1)),
            previous = com.dvnnfrr.saku.domain.PeriodTotals.EMPTY,
            current = com.dvnnfrr.saku.domain.PeriodTotals(Money(8_000_000), Money(5_000_000)),
        )

        assertTrue(!comparison.hasBaseline)
        assertNull(comparison.expenseChangePercent)
        assertEquals(Money(5_000_000), comparison.expenseDelta)
    }

    // Period mode switching.

    @Test
    fun `switching mode while viewing the current period anchors on today`() {
        val august = DateRange.ofMonth(LocalDate(2026, 8, 1))
        val today = LocalDate(2026, 8, 17)

        val weekly = rangeForMode(august, PeriodMode.WEEKLY, today)

        assertEquals(PeriodMode.WEEKLY, weekly.mode)
        assertEquals(LocalDate(2026, 8, 17), weekly.start)
        assertEquals(LocalDate(2026, 8, 23), weekly.endInclusive)
    }

    @Test
    fun `switching mode while viewing a past period stays in that period`() {
        val march = DateRange.ofMonth(LocalDate(2024, 3, 1))
        val today = LocalDate(2026, 8, 17)

        val weekly = rangeForMode(march, PeriodMode.WEEKLY, today)

        assertEquals(LocalDate(2024, 2, 26), weekly.start)
        assertEquals(LocalDate(2024, 3, 3), weekly.endInclusive)
    }

    @Test
    fun `switching to Annually covers the whole year of the period being left`() {
        val range = rangeForMode(
            DateRange.ofMonth(LocalDate(2025, 11, 1)),
            PeriodMode.ANNUALLY,
            LocalDate(2026, 8, 17),
        )

        assertEquals(LocalDate(2025, 1, 1), range.start)
        assertEquals(LocalDate(2025, 12, 31), range.endInclusive)
    }

    // Labels.

    @Test
    fun `a range reads as explicit start and end dates`() {
        assertEquals(
            "1 Aug 2026 – 31 Aug 2026",
            explicitRangeLabel(DateRange.ofMonth(LocalDate(2026, 8, 1))),
        )
        assertEquals(
            "1 Feb 2026 – 31 Jul 2026",
            explicitRangeLabel(DateRange.custom(LocalDate(2026, 2, 1), LocalDate(2026, 7, 31))),
        )
        assertEquals(
            "17 Aug 2026",
            explicitRangeLabel(DateRange.custom(LocalDate(2026, 8, 17), LocalDate(2026, 8, 17))),
        )
    }

    @Test
    fun `a change always carries its sign`() {
        assertEquals("+12%", formatPercentChange(12))
        assertEquals("-4%", formatPercentChange(-4))
        assertEquals("0%", formatPercentChange(0))
    }
}
