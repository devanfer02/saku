package com.dvnnfrr.saku.ui.stats

import com.dvnnfrr.saku.domain.CategoryTotal
import com.dvnnfrr.saku.domain.ColorSlot
import com.dvnnfrr.saku.domain.DateRange
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.PeriodMode
import com.dvnnfrr.saku.domain.PeriodTotals
import com.dvnnfrr.saku.ui.theme.formatIdrCompact
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

    // buku-kas: ranked rows carry a bar scaled to the largest row.

    @Test
    fun `October expenses rank with bars scaled to Pemberian`() {
        val composition = rankComposition(
            totals(
                "Gym" to 2_244_000,
                "Pemberian" to 4_770_000,
                "Makan & Minum" to 1_263_400,
                "Gadget" to 2_805_000,
                "Jajan & Hiburan" to 2_244_000,
                "Kebutuhan Kos" to 700_000,
            ),
        )

        assertEquals(
            listOf("Pemberian", "Gadget", "Gym", "Jajan & Hiburan", "Makan & Minum", "Kebutuhan Kos"),
            composition.rows.map { it.name },
        )
        assertEquals(listOf(34, 20, 16, 16, 9, 5), composition.rows.map { it.percent })
        assertEquals(1_000, composition.rows.first().barPermille)
        assertEquals(588, composition.rows[1].barPermille)
        assertEquals(147, composition.rows.last().barPermille)
    }

    @Test
    fun `an Other row larger than the top category sets the scale`() {
        val composition = rankComposition(
            totals(
                "A" to 100, "B" to 90, "C" to 90, "D" to 90, "E" to 90,
                "F" to 90, "G" to 90, "H" to 90, "I" to 90, "J" to 90,
            ),
            cap = 8,
        )

        assertEquals(1_000, composition.other?.barPermille)
        assertEquals(556, composition.rows.first().barPermille)
        // The drill into Other draws its members on their own scale.
        assertEquals(listOf(1_000, 1_000), composition.otherMembers.map { it.barPermille })
    }

    @Test
    fun `an all-zero period draws no bars`() {
        val composition = rankComposition(totals("A" to 0, "B" to 0))
        assertEquals(listOf(0, 0), composition.rows.map { it.barPermille })
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

    @Test
    fun `switching to Monthly under a pay cycle lands on the pay month of that name`() {
        val range = rangeForMode(
            DateRange.ofYear(2024),
            PeriodMode.MONTHLY,
            LocalDate(2026, 8, 17),
            payStartDay = 5,
        )

        // The pay month holding 1 Jan 2024 is called December 2023; January starts the 5th.
        assertEquals(DateRange.ofPayMonth(LocalDate(2024, 1, 5), 5), range)
    }

    // The six-month window.

    @Test
    fun `the month window is six calendar months ending with the selected one`() {
        val window = monthWindow(DateRange.ofMonth(LocalDate(2026, 10, 1)))

        assertEquals(
            (5..10).map { DateRange.ofMonth(LocalDate(2026, it, 1)) },
            window,
        )
    }

    @Test
    fun `the month window under a pay cycle is six pay months`() {
        val selected = DateRange.ofPayMonth(LocalDate(2026, 10, 30), 30)

        val window = monthWindow(selected)

        assertEquals(6, window.size)
        assertEquals(selected, window.last())
        assertEquals(LocalDate(2026, 5, 30), window.first().start)
        window.forEach { assertEquals(30, it.payStartDay) }
    }

    @Test
    fun `there is no month window outside Monthly mode`() {
        assertEquals(emptyList<DateRange>(), monthWindow(DateRange.ofYear(2026)))
        assertEquals(emptyList<DateRange>(), monthWindow(DateRange.ofWeek(LocalDate(2026, 10, 30))))
    }

    // Labels.

    @Test
    fun `a pay month's explicit range reads as its span`() {
        val pay = DateRange.ofPayMonth(LocalDate(2026, 10, 30), 25)

        assertEquals("25 Oct to 24 Nov 2026", explicitRangeLabel(pay))
    }

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

    // buku-kas: the comparison line under the period total.

    private val october = DateRange.ofMonth(LocalDate(2026, 10, 1))
    private val september = DateRange.ofMonth(LocalDate(2026, 9, 1))

    @Test
    fun `October expenses read 21 percent less than September`() {
        val comparison = PeriodComparison(
            previousRange = september,
            previous = PeriodTotals(Money(19_155_000), Money(17_755_000)),
            current = PeriodTotals(Money(16_500_000), Money(14_026_400)),
        )

        assertEquals(ComparisonLine.Less(21, september), comparisonLine(comparison, StatsSide.EXPENSES))
    }

    @Test
    fun `a rise reads as more and no movement as the same`() {
        val up = PeriodComparison(september, PeriodTotals(Money(1_000_000), Money(1_000_000)), PeriodTotals(Money(1_120_000), Money(1_000_000)))

        assertEquals(ComparisonLine.More(12, september), comparisonLine(up, StatsSide.INCOME))
        assertEquals(ComparisonLine.Same(september), comparisonLine(up, StatsSide.EXPENSES))
    }

    @Test
    fun `an empty previous period has nothing to compare with`() {
        val comparison = PeriodComparison(september, PeriodTotals.EMPTY, PeriodTotals(Money(16_500_000), Money(14_026_400)))

        assertEquals(ComparisonLine.NothingToCompare(september), comparisonLine(comparison, StatsSide.EXPENSES))
        assertEquals(ComparisonLine.NothingToCompare(september), comparisonLine(comparison, StatsSide.INCOME))
    }

    @Test
    fun `a previous period with nothing on this side also has nothing to compare with`() {
        val comparison = PeriodComparison(september, PeriodTotals(Money(9_000_000), Money.ZERO), PeriodTotals(Money(0), Money(500_000)))

        assertEquals(ComparisonLine.NothingToCompare(september), comparisonLine(comparison, StatsSide.EXPENSES))
    }

    @Test
    fun `no previous range gives no line at all`() {
        assertNull(comparisonLine(PeriodComparison.EMPTY, StatsSide.EXPENSES))
    }

    // buku-kas: the six-month strip.

    /** May to October 2026, with nets of +0,8, +7,1, −0,3, −1,4, +1,4 and +2,5 jt. */
    private val mayToOctober = listOf(
        PeriodTotals(Money(9_000_000), Money(8_200_000)),
        PeriodTotals(Money(16_000_000), Money(8_900_000)),
        PeriodTotals(Money(8_000_000), Money(8_300_000)),
        PeriodTotals(Money(8_000_000), Money(9_383_600)),
        PeriodTotals(Money(19_155_000), Money(17_755_000)),
        PeriodTotals(Money(16_500_000), Money(14_026_400)),
    )

    @Test
    fun `the window is the six months ending with the selected one`() {
        assertEquals(
            (5..10).map { DateRange.ofMonth(LocalDate(2026, it, 1)) },
            stripWindow(october),
        )
        assertEquals(
            DateRange.ofMonth(LocalDate(2025, 11, 1)),
            stripWindow(DateRange.ofMonth(LocalDate(2026, 4, 1))).first(),
        )
    }

    @Test
    fun `history before the window ends the day before it starts`() {
        assertEquals(LocalDate(2026, 4, 30), historyBefore(stripWindow(october)).endInclusive)
    }

    @Test
    fun `October 2026 reads May to October with October selected`() {
        val strip = foldStrip(stripWindow(october), mayToOctober, PeriodTotals(Money(1), Money.ZERO), october)

        assertEquals(listOf(false, false, false, false, false, true), strip.map { it.selected })
        assertEquals(
            listOf("+0,8 jt", "+7,1 jt", "−0,3 jt", "−1,4 jt", "+1,4 jt", "+2,5 jt"),
            strip.map { formatIdrCompact(it.net) },
        )
        assertTrue(strip.none { it.empty })
    }

    @Test
    fun `bars share one scale, the largest figure in the window`() {
        val strip = foldStrip(stripWindow(october), mayToOctober, PeriodTotals(Money(1), Money.ZERO), october)

        // September's 19.155.000 income is the tallest bar.
        assertEquals(1_000, strip[4].incomePermille)
        assertEquals(927, strip[4].expensePermille)
        assertEquals(732, strip[5].expensePermille)
        assertEquals(861, strip[5].incomePermille)
    }

    @Test
    fun `months before the first transaction are empty slots`() {
        val august2025 = DateRange.ofMonth(LocalDate(2025, 8, 1))
        val totals = List(4) { PeriodTotals.EMPTY } +
            listOf(PeriodTotals(Money(4_000_000), Money(3_000_000)), PeriodTotals(Money.ZERO, Money(2_000_000)))

        val strip = foldStrip(stripWindow(august2025), totals, PeriodTotals.EMPTY, august2025)

        assertEquals((3..8).map { DateRange.ofMonth(LocalDate(2025, it, 1)) }, strip.map { it.range })
        assertEquals(listOf(true, true, true, true, false, false), strip.map { it.empty })
    }

    @Test
    fun `a quiet month after history began is a zero, not an empty slot`() {
        val totals = listOf(PeriodTotals(Money(1_000_000), Money.ZERO)) + List(5) { PeriodTotals.EMPTY }
        val strip = foldStrip(stripWindow(october), totals, PeriodTotals.EMPTY, october)

        assertEquals(listOf(false, false, false, false, false, false), strip.map { it.empty })
        assertEquals(0, strip[3].incomePermille)
    }

    @Test
    fun `history before the window means no month is empty`() {
        val strip = foldStrip(stripWindow(october), List(6) { PeriodTotals.EMPTY }, PeriodTotals(Money.ZERO, Money(10)), october)
        assertTrue(strip.none { it.empty })
    }
}
