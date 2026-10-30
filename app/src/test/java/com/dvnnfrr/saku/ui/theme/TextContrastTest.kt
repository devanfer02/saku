package com.dvnnfrr.saku.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.pow

/**
 * The text contrast floor: every token that colours text reaches 4.5:1 against both
 * surfaces text sits on, in both themes.
 *
 * `ink3` is deliberately absent. It measures 3.0:1 on light and 3.8:1 on dark, so it is
 * kept for icons and control outlines (3:1 is the floor there) and never colours text.
 */
class TextContrastTest {

    private fun textTokens(colors: SakuColors): Map<String, Color> = mapOf(
        "ink" to colors.ink,
        "ink2" to colors.ink2,
        "accent" to colors.accent,
        "income" to colors.income,
        "expense" to colors.expense,
        "transfer" to colors.transfer,
        "warning" to colors.warning,
    )

    private fun backgrounds(colors: SakuColors): Map<String, Color> = mapOf(
        "background" to colors.background,
        "surface" to colors.surface,
    )

    /** WCAG 2.x relative luminance, from the sRGB channels. */
    private fun luminance(color: Color): Double {
        fun channel(value: Float): Double {
            val c = value.toDouble()
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }

    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun assertFloor(theme: String, colors: SakuColors) {
        val failures = textTokens(colors).flatMap { (name, ink) ->
            backgrounds(colors).mapNotNull { (surfaceName, surface) ->
                val ratio = contrast(ink, surface)
                if (ratio >= FLOOR) null else "$theme $name on $surfaceName: ${"%.2f".format(ratio)}"
            }
        }
        assertTrue("Below 4.5:1: $failures", failures.isEmpty())
    }

    @Test
    fun `every text token reaches the floor in the light theme`() {
        assertFloor("light", LightSakuColors)
    }

    @Test
    fun `every text token reaches the floor in the dark theme`() {
        assertFloor("dark", DarkSakuColors)
    }

    @Test
    fun `ink3 is below the floor, which is why it carries no text`() {
        // If a palette change lifts ink3 over the floor this test is the reminder that
        // the rule can be revisited; until then it documents the measurement.
        assertTrue(contrast(LightSakuColors.ink3, LightSakuColors.surface) < FLOOR)
        assertTrue(contrast(DarkSakuColors.ink3, DarkSakuColors.surface) < FLOOR)
    }

    /**
     * A source scan in the manner of `NoHardCodedColorsTest`: no line outside the theme
     * package hands `ink3` to anything but an icon tint or a control's outline. Text
     * takes its colour through a `color` argument, so that is what is searched for.
     */
    @Test
    fun `ink3 colours no text outside the theme package`() {
        val uiRoot = File("src/main/java/com/dvnnfrr/saku/ui")
        assertTrue("ui source root not found at ${uiRoot.absolutePath}", uiRoot.isDirectory)
        val textUse = Regex("""(^|[^A-Za-z])color\s*=.*\bink3\b|->\s*\S*ink3\b""")

        val offenders = uiRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.parentFile.name != "theme" }
            .flatMap { file ->
                file.readLines().withIndex()
                    .filter { (_, line) -> textUse.containsMatchIn(line) }
                    .map { (index, line) -> "${file.name}:${index + 1}  ${line.trim()}" }
            }
            .toList()

        assertTrue("ink3 used for text:\n${offenders.joinToString("\n")}", offenders.isEmpty())
    }

    private companion object {
        const val FLOOR = 4.5
    }
}
