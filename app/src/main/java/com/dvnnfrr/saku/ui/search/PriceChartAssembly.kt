package com.dvnnfrr.saku.ui.search

import com.dvnnfrr.saku.domain.Money
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/*
 * Pure assembly for the price chart: which ranges to offer, the points a range shows, one
 * point per month over a long range, the Y axis and the point nearest a touch. No Compose,
 * no Android. The design is in openspec/changes/price-chart-rework/design.md.
 */

/** How far back the chart looks from the latest point. [months] is null for the whole history. */
enum class PriceRange(val months: Int?) {
    SIX_MONTHS(6),
    ONE_YEAR(12),
    THREE_YEARS(36),
    ALL(null),
}

/** The first day [range] keeps, counted back from [latest]; null for [PriceRange.ALL]. */
fun PriceRange.startFrom(latest: LocalDate): LocalDate? = months?.let { latest.minus(it, DateTimeUnit.MONTH) }

/**
 * The ranges worth a chip, shortest first: each one that holds two points or more and
 * fewer than the next longer range, then [PriceRange.ALL]. A range that would show the
 * same points as a longer one is not offered, so a short history gets All alone and no
 * chips at all, and a 6M that shows what 1Y shows is left out.
 */
fun PriceHistory.ranges(): List<PriceRange> {
    val counts = PriceRange.entries.associateWith { range ->
        val start = range.startFrom(latest.date) ?: return@associateWith points.size
        points.count { it.date >= start }
    }
    return PriceRange.entries.filterIndexed { index, range ->
        val count = counts.getValue(range)
        val longer = PriceRange.entries.getOrNull(index + 1)?.let(counts::getValue)
        longer == null || (count >= 2 && count < longer)
    }
}

/** One year when the history is longer and a year holds two points, else the whole history. */
fun PriceHistory.defaultRange(): PriceRange =
    if (PriceRange.ONE_YEAR in ranges()) PriceRange.ONE_YEAR else PriceRange.ALL

/**
 * The card for [range]: its points and the figures over them. The whole history when
 * [range] holds under two points, which [ranges] never offers.
 */
fun PriceHistory.inRange(range: PriceRange): PriceHistory {
    val start = range.startFrom(latest.date) ?: return this
    val kept = points.filter { it.date >= start }
    if (kept.size < 2 || kept.size == points.size) return this
    return historyOf(itemKey, name, kept)
}

/**
 * One point the chart draws. [position] is its x in days since the epoch. On a monthly
 * chart [date] is the first of the month and [unitPrice] the month's average.
 */
data class ChartPoint(val position: Long, val date: LocalDate, val unitPrice: Money)

/** The Y axis: gridlines from [low] to [high] every [step], all whole rupiah. */
data class PriceAxis(val low: Money, val high: Money, val step: Money) {
    val ticks: List<Money>
        get() = (low.rupiah..high.rupiah step step.rupiah).map(::Money)
}

/** What the chart draws: its [points] oldest first, whether they are [monthly], and the [axis]. */
data class PriceChart(val points: List<ChartPoint>, val monthly: Boolean, val axis: PriceAxis)

/**
 * The chart over [points], oldest first. Over more than [MONTHLY_AFTER_MONTHS] months with
 * more than [MONTHLY_AFTER_POINTS] days, it draws one point per month at the month's
 * average, so ten years of weekly shopping is 120 points, not a smear of 520 days. A few
 * trips a year stay as days: averaging them would only hide the real prices.
 */
fun priceChart(points: List<PricePoint>): PriceChart {
    val monthly = points.size > MONTHLY_AFTER_POINTS &&
        points.first().date.plus(MONTHLY_AFTER_MONTHS, DateTimeUnit.MONTH) < points.last().date
    val drawn = if (monthly) monthlyPoints(points) else points.map { ChartPoint(it.date.toEpochDays().toLong(), it.date, it.unitPrice) }
    val axis = priceAxis(drawn.minOf { it.unitPrice }, drawn.maxOf { it.unitPrice })
    return PriceChart(drawn, monthly, axis)
}

/** One point per month with a price, at the first of the month, its average rounded half up. */
internal fun monthlyPoints(points: List<PricePoint>): List<ChartPoint> =
    points
        .groupBy { LocalDate(it.date.year, it.date.month, 1) }
        .map { (month, days) -> ChartPoint(month.toEpochDays().toLong(), month, averageOf(days.map { it.unitPrice })) }
        .sortedBy { it.position }

/**
 * Round gridlines around prices from [min] to [max]: the smallest step of 1, 2, 2,5 or 5
 * times a power of ten, at least [MIN_PRICE_STEP], that spans them in at most
 * [MAX_PRICE_STEPS] steps, so 3 or 4 lines. The axis starts at a multiple of the step at
 * or below [min], not at zero: a price moving between 35 rb and 41 rb would be a flat line
 * on an axis from 0. A flat price gets a band a tenth of it wide either side, never below 0.
 */
fun priceAxis(min: Money, max: Money): PriceAxis {
    var low = min.rupiah.coerceAtLeast(0)
    var high = max.rupiah.coerceAtLeast(low)
    if (high == low) {
        val pad = (high / 10).coerceAtLeast(MIN_PRICE_STEP)
        low = (low - pad).coerceAtLeast(0)
        high += pad
    }
    var base = MIN_PRICE_STEP
    while (true) {
        for (step in longArrayOf(base, base * 2, base * 5 / 2, base * 5)) {
            val bottom = low / step * step
            val top = (high + step - 1) / step * step
            if ((top - bottom) / step <= MAX_PRICE_STEPS) return PriceAxis(Money(bottom), Money(top), Money(step))
        }
        base *= 10
    }
}

/**
 * The index of the position nearest [target] among [positions], ascending and not empty;
 * the earlier one on a tie. What a tap or a drag at some x selects.
 */
fun nearestPoint(positions: List<Long>, target: Double): Int {
    var lo = 0
    var hi = positions.lastIndex
    while (lo < hi) {
        val mid = (lo + hi) / 2
        if (positions[mid] < target) lo = mid + 1 else hi = mid
    }
    if (lo > 0 && target - positions[lo - 1] <= positions[lo] - target) return lo - 1
    return lo
}

private const val MONTHLY_AFTER_MONTHS = 18
private const val MONTHLY_AFTER_POINTS = 60
private const val MIN_PRICE_STEP = 100L
private const val MAX_PRICE_STEPS = 3L
