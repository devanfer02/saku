package com.dvnnfrr.saku.ui.stats

import com.dvnnfrr.saku.domain.CategoryTotal
import com.dvnnfrr.saku.domain.ColorSlot
import com.dvnnfrr.saku.domain.DailyTotals
import com.dvnnfrr.saku.domain.DateRange
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.PeriodMode
import com.dvnnfrr.saku.domain.PeriodTotals
import com.dvnnfrr.saku.ui.theme.formatIdrCompact
import com.dvnnfrr.saku.domain.Subcategory
import com.dvnnfrr.saku.domain.Transaction
import com.dvnnfrr.saku.domain.TransactionFilter
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

    // buku-kas: the strip over all history, in weeks, months or years.

    /** One day a month carrying the month's totals, May to October 2026. */
    private val mayToOctober = listOf(
        9_000_000L to 8_200_000L,
        16_000_000L to 8_900_000L,
        8_000_000L to 8_300_000L,
        8_000_000L to 9_383_600L,
        19_155_000L to 17_755_000L,
        16_500_000L to 14_026_400L,
    ).mapIndexed { index, (income, expense) ->
        DailyTotals(LocalDate(2026, 5 + index, 3), Money(income), Money(expense))
    }

    private fun day(year: Int, month: Int, day: Int, income: Long = 0, expense: Long = 0) =
        DailyTotals(LocalDate(year, month, day), Money(income), Money(expense))

    @Test
    fun `the strip reads from the start of history to the end of the selected period`() {
        val history = stripHistory(october)
        assertEquals(LocalDate(2026, 10, 31), history.endInclusive)
        assertTrue(history.start < LocalDate(2000, 1, 1))
    }

    @Test
    fun `October 2026 reads from the first month with a flow, October selected and last`() {
        val strip = foldStrip(mayToOctober, october)

        assertEquals((5..10).map { DateRange.ofMonth(LocalDate(2026, it, 1)) }, strip.map { it.range })
        assertEquals(listOf(false, false, false, false, false, true), strip.map { it.selected })
        assertEquals(
            listOf("+0,8 jt", "+7,1 jt", "−0,3 jt", "−1,4 jt", "+1,4 jt", "+2,5 jt"),
            strip.map { formatIdrCompact(it.net) },
        )
        assertTrue(strip.none { it.empty })
    }

    @Test
    fun `history from July 2025 holds every month through October 2026`() {
        val days = listOf(day(2025, 7, 20, expense = 50_000)) + mayToOctober

        val strip = foldStrip(days, october)

        assertEquals(16, strip.size)
        assertEquals(DateRange.ofMonth(LocalDate(2025, 7, 1)), strip.first().range)
        assertEquals(october, strip.last().range)
        // August 2025 to April 2026 are quiet months after history began: real zeros.
        assertEquals(Money.ZERO, strip[1].expense)
        assertTrue(strip.none { it.empty })
    }

    @Test
    fun `bars share one scale across the whole history`() {
        val strip = foldStrip(mayToOctober, october)

        // September's 19.155.000 income is the tallest bar.
        assertEquals(1_000, strip[4].incomePermille)
        assertEquals(927, strip[4].expensePermille)
        assertEquals(732, strip[5].expensePermille)
        assertEquals(861, strip[5].incomePermille)
    }

    @Test
    fun `an earlier selected month ends the strip there`() {
        val january = DateRange.ofMonth(LocalDate(2026, 1, 1))
        val days = listOf(day(2025, 7, 20, expense = 50_000)) + mayToOctober

        val strip = foldStrip(days, january)

        assertEquals(january, strip.last().range)
        assertTrue(strip.last().selected)
        assertEquals(7, strip.size)
    }

    @Test
    fun `days after the selected month do not start history`() {
        val april = DateRange.ofMonth(LocalDate(2026, 4, 1))

        val strip = foldStrip(mayToOctober, april)

        assertEquals(listOf(april), strip.map { it.range })
        assertTrue(strip.single().empty)
    }

    @Test
    fun `no history at all is the selected month alone as an empty slot`() {
        val strip = foldStrip(emptyList(), october)

        assertEquals(1, strip.size)
        assertTrue(strip.single().empty)
        assertTrue(strip.single().selected)
    }

    @Test
    fun `Weekly buckets days into Monday to Sunday weeks`() {
        val week = DateRange.ofWeek(LocalDate(2026, 10, 26))
        val days = listOf(
            day(2026, 10, 11, expense = 100_000), // Sunday, week of 5 Oct
            day(2026, 10, 12, expense = 200_000), // Monday, week of 12 Oct
            day(2026, 10, 18, expense = 300_000), // Sunday, week of 12 Oct
            day(2026, 10, 27, income = 400_000),
        )

        val strip = foldStrip(days, week)

        assertEquals(
            listOf(5, 12, 19, 26).map { DateRange.ofWeek(LocalDate(2026, 10, it)) },
            strip.map { it.range },
        )
        assertEquals(listOf(100_000L, 500_000L, 0L, 0L), strip.map { it.expense.rupiah })
        assertEquals(Money(400_000), strip.last().income)
        assertTrue(strip.last().selected)
    }

    @Test
    fun `Annually reads 2025 and 2026`() {
        val days = listOf(day(2025, 7, 20, expense = 50_000)) + mayToOctober

        val strip = foldStrip(days, DateRange.ofYear(2026))

        assertEquals(listOf(DateRange.ofYear(2025), DateRange.ofYear(2026)), strip.map { it.range })
        assertEquals(Money(50_000), strip.first().expense)
        assertEquals(Money(76_655_000), strip.last().income)
    }

    @Test
    fun `Period mode has no strip`() {
        val custom = DateRange.custom(LocalDate(2026, 10, 3), LocalDate(2026, 10, 20))
        assertTrue(foldStrip(mayToOctober, custom).isEmpty())
        assertTrue(!hasStrip(PeriodMode.CUSTOM))
    }

    // buku-kas: the pie and the category detail.

    private val octoberExpenses = totals(
        "Pemberian" to 4_821_600,
        "Gadget" to 2_805_000,
        "Gym" to 2_244_000,
        "Jajan & Hiburan" to 2_244_000,
        "Makan & Minum" to 1_211_800,
        "Kebutuhan Kos" to 700_000,
    )

    @Test
    fun `pie sweeps add up to exactly one turn`() {
        val composition = rankComposition(octoberExpenses)

        assertEquals(PIE_WHOLE, composition.rows.sumOf { it.sweepTenths })
        assertEquals(34, composition.rows.first().percent)
        // 4.821.600 of 14.026.400 is 123.75 degrees, carried in tenths.
        assertEquals(1_237, composition.rows.first().sweepTenths)
    }

    @Test
    fun `awkward thirds still close the circle`() {
        val sweeps = pieSweeps(listOf(Money(1), Money(1), Money(1)))
        assertEquals(listOf(1_200, 1_200, 1_200), sweeps)
        assertEquals(PIE_WHOLE, pieSweeps(listOf(Money(7), Money(11), Money(13))).sum())
    }

    @Test
    fun `nothing positive draws no slices`() {
        assertEquals(listOf(0, 0), pieSweeps(listOf(Money.ZERO, Money(-5))))
        assertEquals(listOf(0, PIE_WHOLE), pieSweeps(listOf(Money(-5), Money(10))))
    }

    @Test
    fun `the pie keeps the eight-slot cap with Other as one slice`() {
        val composition = rankComposition(
            totals("A" to 100, "B" to 90, "C" to 90, "D" to 90, "E" to 90, "F" to 90, "G" to 90, "H" to 90, "I" to 90, "J" to 90),
        )

        assertEquals(9, composition.rows.size)
        assertEquals(PIE_WHOLE, composition.rows.sumOf { it.sweepTenths })
        assertTrue(composition.rows.last().isOther)
    }

    @Test
    fun `Pemberian reads 34 percent of October spending, as its ranked row does`() {
        val composition = rankComposition(octoberExpenses)
        assertEquals(34, shareOf(composition, composition.rows.first { it.name == "Pemberian" }.categoryId!!))
        assertEquals(0, shareOf(composition, 999))
    }

    @Test
    fun `a member of Other keeps the share it had in the ranked list`() {
        val composition = rankComposition(
            totals("A" to 100, "B" to 90, "C" to 90, "D" to 90, "E" to 90, "F" to 90, "G" to 90, "H" to 90, "I" to 90, "J" to 90),
        )
        val member = composition.otherMembers.first()
        assertEquals(member.percent, shareOf(composition, member.categoryId!!))
    }

    @Test
    fun `the average rounds half up to the rupiah`() {
        assertEquals(Money(1_607_200), averagePerTransaction(Money(4_821_600), 3))
        assertEquals(Money(250_001), averagePerTransaction(Money(500_001), 2))
        assertEquals(Money(250_000), averagePerTransaction(Money(500_000), 2))
        assertEquals(Money.ZERO, averagePerTransaction(Money(100), 0))
    }

    @Test
    fun `the category filter narrows one side and keeps the accounts`() {
        val filter = TransactionFilter(accountIds = setOf(3L))

        val expense = categoryFilter(filter, StatsSide.EXPENSES, 7)
        assertEquals(setOf(7L), expense.expenseCategoryIds)
        assertEquals(emptySet<Long>(), expense.incomeCategoryIds)
        assertEquals(setOf(3L), expense.accountIds)

        val income = categoryFilter(TransactionFilter.ALL, StatsSide.INCOME, 2)
        assertEquals(setOf(2L), income.incomeCategoryIds)
        assertEquals(emptySet<Long>(), income.expenseCategoryIds)
    }

    @Test
    fun `subcategories step down in strength and the unassigned bucket is neutral`() {
        val slices = foldSubcategories(
            listOf(txn(1, 3_000_000, 1), txn(2, 1_200_000, 2), txn(3, 621_600, null)),
            subcategories,
            ColorSlot(0),
        )

        assertEquals(listOf(100, 82, null), slices.map { it.strengthPercent })
        assertEquals(PIE_WHOLE, slices.sumOf { it.sweepTenths })
        assertEquals(28, subcategoryStrength(20))
    }
}
