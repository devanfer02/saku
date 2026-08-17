package com.dvnnfrr.saku.ui.theme

import com.dvnnfrr.saku.domain.Money
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
    fun `a negative amount carries its sign ahead of the prefix`() {
        assertEquals("-Rp 5.061.400", formatIdr(Money(-5_061_400)))
        assertEquals("-5.061.400", formatIdr(Money(-5_061_400), withPrefix = false))
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
        assertEquals("-Rp 9.223.372.036.854.775.808", formatIdr(Money(Long.MIN_VALUE)))
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
        assertEquals("-Rp 250.000", formatIdrSigned(Money(-250_000)))
        assertEquals("Rp 0", formatIdrSigned(Money.ZERO))
        assertEquals("+250.000", formatIdrSigned(Money(250_000), withPrefix = false))
    }
}
