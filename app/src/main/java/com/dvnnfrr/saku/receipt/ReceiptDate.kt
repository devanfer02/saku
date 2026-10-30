package com.dvnnfrr.saku.receipt

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/** A date read from [rows], and the row it was on. */
data class DatePick(val date: LocalDate, val rowIndex: Int)

private val DAY_FIRST = Regex("""(?<!\d)(\d{1,2})[/.\-](\d{1,2})[/.\-](\d{4}|\d{2})(?!\d)""")
private val YEAR_FIRST = Regex("""(?<!\d)(\d{4})[/.\-](\d{1,2})[/.\-](\d{1,2})(?!\d)""")
private val MONTH_NAME = Regex(
    """(?<!\d)(\d{1,2})\s*(jan|feb|mar|apr|mei|may|jun|jul|agu|agt|aug|sep|okt|oct|nov|des|dec)[a-z]*\.?\s*(\d{4}|\d{2})(?!\d)""",
    RegexOption.IGNORE_CASE,
)

/** Indonesian and English abbreviations; both appear, since many apps print English. */
private val MONTHS = mapOf(
    "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "mei" to 5, "may" to 5, "jun" to 6,
    "jul" to 7, "agu" to 8, "agt" to 8, "aug" to 8, "sep" to 9, "okt" to 10, "oct" to 10,
    "nov" to 11, "des" to 12, "dec" to 12,
)

/**
 * The first date in printed order that falls between a year before [today] and [today].
 *
 * The window is the guard against a misread: a future date or one from years ago is far
 * more likely a misread digit, or a promotion's expiry, than the day of the purchase, and
 * it would quietly file the transaction under the wrong month.
 */
fun pickDate(rows: List<ReceiptRow>, today: LocalDate): DatePick? {
    val earliest = today.minus(DatePeriod(years = 1))
    rows.forEachIndexed { index, row ->
        val found = datesIn(row.text).firstOrNull { it in earliest..today }
        if (found != null) return DatePick(found, index)
    }
    return null
}

private fun datesIn(text: String): List<LocalDate> {
    val named = MONTH_NAME.findAll(text).mapNotNull { m ->
        val month = MONTHS[m.groupValues[2].lowercase()] ?: return@mapNotNull null
        date(m.groupValues[3], month, m.groupValues[1].toInt())
    }
    val yearFirst = YEAR_FIRST.findAll(text).mapNotNull { m ->
        date(m.groupValues[1], m.groupValues[2].toInt(), m.groupValues[3].toInt())
    }
    val dayFirst = DAY_FIRST.findAll(text).mapNotNull { m ->
        date(m.groupValues[3], m.groupValues[2].toInt(), m.groupValues[1].toInt())
    }
    return (named + yearFirst + dayFirst).toList()
}

private fun date(year: String, month: Int, day: Int): LocalDate? {
    val fullYear = year.toInt().let { if (year.length == 2) 2000 + it else it }
    return runCatching { LocalDate(fullYear, month, day) }.getOrNull()
}
