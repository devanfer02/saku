package com.dvnnfrr.saku.ui.theme

import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.TxnType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class FormatIdrTest {

    private val originalLocale: Locale = Locale.getDefault()

    @After
    fun restoreLocale() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun `formats the reference amount with full stops and the Rp prefix`() {
        assertEquals("Rp 5.061.400", formatIdr(Money(5_061_400)))
    }

    @Test
    fun `the compact variant drops the prefix and keeps the grouping`() {
        assertEquals("5.061.400", formatIdr(Money(5_061_400), withPrefix = false))
    }

    @Test
    fun `zero formats as a plain zero rather than an empty string`() {
        assertEquals("Rp 0", formatIdr(Money.ZERO))
        assertEquals("0", formatIdr(Money.ZERO, withPrefix = false))
    }

    @Test
    fun `a negative amount carries the minus sign ahead of the prefix`() {
        assertEquals("−Rp 5.061.400", formatIdr(Money(-5_061_400)))
        assertEquals("−5.061.400", formatIdr(Money(-5_061_400), withPrefix = false))
    }

    @Test
    fun `amounts below a thousand take no separator`() {
        assertEquals("Rp 0", formatIdr(Money(0)))
        assertEquals("Rp 7", formatIdr(Money(7)))
        assertEquals("Rp 99", formatIdr(Money(99)))
        assertEquals("Rp 999", formatIdr(Money(999)))
    }

    @Test
    fun `group boundaries land in the right places`() {
        assertEquals("Rp 1.000", formatIdr(Money(1_000)))
        assertEquals("Rp 12.500", formatIdr(Money(12_500)))
        assertEquals("Rp 100.000", formatIdr(Money(100_000)))
        assertEquals("Rp 1.000.000", formatIdr(Money(1_000_000)))
        assertEquals("Rp 1.000.000.000", formatIdr(Money(1_000_000_000)))
    }

    @Test
    fun `the extremes of Long format rather than overflowing`() {
        assertEquals("Rp 9.223.372.036.854.775.807", formatIdr(Money(Long.MAX_VALUE)))
        assertEquals("−Rp 9.223.372.036.854.775.808", formatIdr(Money(Long.MIN_VALUE)))
    }

    @Test
    fun `a non-Indonesian default locale does not change the separator`() {
        val amount = Money(5_061_400)
        val locales = listOf(
            Locale.US,
            Locale.UK,
            Locale.GERMANY,
            Locale.FRANCE,
            Locale.JAPAN,
            Locale.forLanguageTag("ar-EG"),
            Locale.forLanguageTag("id-ID"),
        )
        for (locale in locales) {
            Locale.setDefault(locale)
            assertEquals(
                "Rp 5.061.400 under $locale",
                "Rp 5.061.400",
                formatIdr(amount),
            )
        }
    }

    @Test
    fun `a locale with non-ASCII digits still renders ASCII digits`() {
        Locale.setDefault(Locale.forLanguageTag("ar-EG-u-nu-arab"))
        assertEquals("Rp 5.061.400", formatIdr(Money(5_061_400)))
    }

    @Test
    fun `the signed variant marks a positive amount and leaves a negative one alone`() {
        assertEquals("+Rp 250.000", formatIdrSigned(Money(250_000)))
        assertEquals("−Rp 250.000", formatIdrSigned(Money(-250_000)))
        assertEquals("Rp 0", formatIdrSigned(Money.ZERO))
        assertEquals("+250.000", formatIdrSigned(Money(250_000), withPrefix = false))
    }

    // Buku Kas: one format, the sign carries the direction.

    @Test
    fun `headers, day totals and rows agree on the October figures`() {
        val left = Money(16_500_000) - Money(14_026_400)
        assertEquals("+Rp 2.473.600", formatMoney(left, MoneySign.NET))
        assertEquals("−Rp 261.000", formatMoney(Money(-261_000), MoneySign.NET))
        assertEquals("−Rp 196.000", formatMoney(Money(196_000), MoneySign.FLOW, TxnType.EXPENSE))
    }

    @Test
    fun `a flow takes its sign from the type`() {
        assertEquals("+Rp 16.500.000", formatMoney(Money(16_500_000), MoneySign.FLOW, TxnType.INCOME))
        assertEquals("−Rp 30.000", formatMoney(Money(30_000), MoneySign.FLOW, TxnType.EXPENSE))
        assertEquals("Rp 1.000.000", formatMoney(Money(1_000_000), MoneySign.FLOW, TxnType.TRANSFER))
    }

    @Test
    fun `an adjustment keeps the sign of its correction`() {
        assertEquals("−Rp 25.000", formatMoney(Money(-25_000), MoneySign.FLOW, TxnType.ADJUSTMENT))
        assertEquals("Rp 25.000", formatMoney(Money(25_000), MoneySign.FLOW, TxnType.ADJUSTMENT))
    }

    @Test
    fun `a balance shows a sign only when negative`() {
        assertEquals("−Rp 1.038.500", formatMoney(Money(-1_038_500), MoneySign.BALANCE))
        assertEquals("Rp 17.557.615", formatMoney(Money(17_557_615), MoneySign.BALANCE))
    }

    @Test
    fun `zero carries no sign in any mode`() {
        for (sign in MoneySign.entries) {
            assertEquals("Rp 0", formatMoney(Money.ZERO, sign, TxnType.INCOME))
        }
    }

    @Test
    fun `the minus is the typographic minus, not a hyphen`() {
        assertEquals(MINUS_SIGN, formatIdr(Money(-1)).first())
        assertEquals('−', MINUS_SIGN)
    }

    // Compact form for chart labels.

    @Test
    fun `compact nets round half up to one decimal of a juta`() {
        assertEquals("+2,5 jt", formatIdrCompact(Money(2_473_600)))
        assertEquals("−1,4 jt", formatIdrCompact(Money(-1_383_600)))
    }

    @Test
    fun `compact rounding is half away from zero on the magnitude`() {
        assertEquals("+0,1 jt", formatIdrCompact(Money(50_000)))
        assertEquals("0 jt", formatIdrCompact(Money(49_999)))
        assertEquals("−0,1 jt", formatIdrCompact(Money(-50_000)))
        assertEquals("0 jt", formatIdrCompact(Money(-49_999)))
        assertEquals("+1,0 jt", formatIdrCompact(Money(999_999)))
    }

    @Test
    fun `compact zero claims no direction`() {
        assertEquals("0 jt", formatIdrCompact(Money.ZERO))
    }

    @Test
    fun `compact millions keep full-stop grouping`() {
        assertEquals("+1.234,6 jt", formatIdrCompact(Money(1_234_560_000)))
    }

    @Test
    fun `compact handles the extremes of Long`() {
        assertEquals(MINUS_SIGN, formatIdrCompact(Money(Long.MIN_VALUE)).first())
        assertEquals('+', formatIdrCompact(Money(Long.MAX_VALUE)).first())
    }

    // Calendar cell form.

    @Test
    fun `a cell net fits a calendar cell`() {
        assertEquals("0", formatIdrCell(Money.ZERO))
        assertEquals("45k", formatIdrCell(Money(45_000)))
        assertEquals("−170k", formatIdrCell(Money(-170_000)))
        assertEquals("5m", formatIdrCell(Money(5_061_400)))
        assertEquals("−5m", formatIdrCell(Money(-5_061_400)))
    }

    @Test
    fun `a cell net under a thousand does not round away to zero`() {
        assertEquals("<1k", formatIdrCell(Money(400)))
        assertEquals("−<1k", formatIdrCell(Money(-400)))
    }

    @Test
    fun `a cell net rounds rather than truncating`() {
        assertEquals("2m", formatIdrCell(Money(1_999_000)))
        assertEquals("−2m", formatIdrCell(Money(-1_980_000)))
        assertEquals("1k", formatIdrCell(Money(500)))
    }
}
