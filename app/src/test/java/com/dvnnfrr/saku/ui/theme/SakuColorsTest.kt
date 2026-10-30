package com.dvnnfrr.saku.ui.theme

import androidx.compose.ui.graphics.Color
import com.dvnnfrr.saku.domain.ColorSlot
import com.dvnnfrr.saku.domain.TxnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier as JavaModifier

/**
 * Guards the two things about the palette that are easy to break by accident: a token
 * that exists in only one theme, and a hex value quietly substituted for one that was
 * never colour-vision validated.
 */
class SakuColorsTest {

    private fun namedTokens(colors: SakuColors): Map<String, Color> = mapOf(
        "background" to colors.background,
        "surface" to colors.surface,
        "surfaceAlt" to colors.surfaceAlt,
        "ink" to colors.ink,
        "ink2" to colors.ink2,
        "ink3" to colors.ink3,
        "line" to colors.line,
        "accent" to colors.accent,
        "accentWash" to colors.accentWash,
        "income" to colors.income,
        "expense" to colors.expense,
        "transfer" to colors.transfer,
        "chartNeutral" to colors.chartNeutral,
        "warning" to colors.warning,
        "netSpendable" to colors.netSpendable,
        "netInvested" to colors.netInvested,
        "inkDisabled" to colors.inkDisabled,
    )

    private val expectedLight = mapOf(
        "background" to Color(0xFFF4F6FB),
        "surface" to Color(0xFFFFFFFF),
        "surfaceAlt" to Color(0xFFE9EDF6),
        "ink" to Color(0xFF0E1626),
        "ink2" to Color(0xFF59637A),
        "ink3" to Color(0xFF8B94A8),
        "line" to Color(0xFFDDE3EF),
        "accent" to Color(0xFF1B5AD9),
        "accentWash" to Color(0xFFE6EDFC),
        "income" to Color(0xFF0B7A5B),
        "expense" to Color(0xFFBF3B2E),
        "transfer" to Color(0xFF59637A),
        "chartNeutral" to Color(0xFF9AA4B8),
        "warning" to Color(0xFF9A6207),
        "netSpendable" to Color(0xFF1B5AD9),
        "netInvested" to Color(0xFFC9541A),
        "inkDisabled" to Color(0xFFB3BBCB),
    )

    private val expectedLightChart = listOf(
        Color(0xFF1B5AD9),
        Color(0xFFC2741C),
        Color(0xFF00846B),
        Color(0xFFD45E96),
        Color(0xFFB08A00),
        Color(0xFF4B3BA8),
        Color(0xFF2F93C7),
        Color(0xFFA6392F),
    )

    private val expectedDark = mapOf(
        "background" to Color(0xFF0A0F1E),
        "surface" to Color(0xFF131A2C),
        "surfaceAlt" to Color(0xFF1C2540),
        "ink" to Color(0xFFE9EDF7),
        "ink2" to Color(0xFF98A3BC),
        "ink3" to Color(0xFF6A7591),
        "line" to Color(0xFF232D48),
        "accent" to Color(0xFF6C9BF0),
        "accentWash" to Color(0xFF17233F),
        "income" to Color(0xFF3BC492),
        "expense" to Color(0xFFF0736A),
        "transfer" to Color(0xFF98A3BC),
        "chartNeutral" to Color(0xFF6E7A94),
        "warning" to Color(0xFFD79A3C),
        "netSpendable" to Color(0xFF6C9BF0),
        "netInvested" to Color(0xFFF08A52),
        "inkDisabled" to Color(0xFF4C566E),
    )

    private val expectedDarkChart = listOf(
        Color(0xFF5688DD),
        Color(0xFFB0651C),
        Color(0xFF2FA882),
        Color(0xFFA8437F),
        Color(0xFF9C8933),
        Color(0xFF9265D2),
        Color(0xFF3E97BE),
        Color(0xFFA85A3C),
    )

    /**
     * The reflection is the point: adding a field to [SakuColors] without adding it to
     * the expectation map fails here, so a new token cannot slip in half-checked.
     */
    @Test
    fun `the expectation covers every field the token set declares`() {
        val declared = SakuColors::class.java.declaredFields
            .filterNot { JavaModifier.isStatic(it.modifiers) }
            .map { it.name }
            .toSet()
        val covered = expectedLight.keys + "chart"
        assertEquals(covered, declared)
    }

    @Test
    fun `the light token set holds the validated values`() {
        assertEquals(expectedLight, namedTokens(LightSakuColors))
        assertEquals(expectedLightChart, LightSakuColors.chart)
    }

    @Test
    fun `the dark token set holds the validated values`() {
        assertEquals(expectedDark, namedTokens(DarkSakuColors))
        assertEquals(expectedDarkChart, DarkSakuColors.chart)
    }

    @Test
    fun `both themes define every token`() {
        val light = namedTokens(LightSakuColors)
        val dark = namedTokens(DarkSakuColors)
        assertEquals(light.keys, dark.keys)
        assertEquals(ColorSlot.COUNT, LightSakuColors.chart.size)
        assertEquals(ColorSlot.COUNT, DarkSakuColors.chart.size)
    }

    @Test
    fun `no token is shared between the themes, so neither was derived from the other`() {
        val light = namedTokens(LightSakuColors)
        val dark = namedTokens(DarkSakuColors)
        for (name in light.keys) {
            assertNotEquals("$name is identical in both themes", light[name], dark[name])
        }
        for (index in 0 until ColorSlot.COUNT) {
            assertNotEquals(
                "chart slot $index is identical in both themes",
                LightSakuColors.chart[index],
                DarkSakuColors.chart[index],
            )
        }
    }

    @Test
    fun `every token is fully opaque`() {
        val all = namedTokens(LightSakuColors).values + LightSakuColors.chart +
            namedTokens(DarkSakuColors).values + DarkSakuColors.chart
        for (color in all) {
            assertEquals("$color is not opaque", 1f, color.alpha, 0f)
        }
    }

    @Test
    fun `each theme's chart slots are eight distinct colours`() {
        assertEquals(ColorSlot.COUNT, LightSakuColors.chart.toSet().size)
        assertEquals(ColorSlot.COUNT, DarkSakuColors.chart.toSet().size)
    }

    @Test
    fun `forSlot resolves a slot to its pinned colour in the active theme`() {
        for (index in 0 until ColorSlot.COUNT) {
            val slot = ColorSlot(index)
            assertEquals(expectedLightChart[index], LightSakuColors.forSlot(slot))
            assertEquals(expectedDarkChart[index], DarkSakuColors.forSlot(slot))
        }
    }

    @Test
    fun `forDirection maps each type to its semantic colour`() {
        for (colors in listOf(LightSakuColors, DarkSakuColors)) {
            assertEquals(colors.income, colors.forDirection(TxnType.INCOME))
            assertEquals(colors.expense, colors.forDirection(TxnType.EXPENSE))
            assertEquals(colors.transfer, colors.forDirection(TxnType.TRANSFER))
            assertEquals(colors.transfer, colors.forDirection(TxnType.ADJUSTMENT))
        }
    }

    @Test
    fun `forDirection never returns the accent`() {
        for (colors in listOf(LightSakuColors, DarkSakuColors)) {
            for (type in TxnType.entries) {
                assertNotEquals(
                    "$type resolved to the accent",
                    colors.accent,
                    colors.forDirection(type),
                )
            }
        }
    }

    @Test
    fun `income and expense are distinguishable from each other and from neutral`() {
        for (colors in listOf(LightSakuColors, DarkSakuColors)) {
            val directions = TxnType.entries.map { colors.forDirection(it) }.toSet()
            assertEquals("income, expense and neutral must be three colours", 3, directions.size)
        }
    }

    @Test
    fun `the accent is a blue in both themes`() {
        for (colors in listOf(LightSakuColors, DarkSakuColors)) {
            assertTrue(
                "accent ${colors.accent} is not blue-dominant",
                colors.accent.blue > colors.accent.red && colors.accent.blue > colors.accent.green,
            )
        }
    }
}
