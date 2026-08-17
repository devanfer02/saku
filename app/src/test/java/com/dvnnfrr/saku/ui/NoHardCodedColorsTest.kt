package com.dvnnfrr.saku.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * No colour literal may live outside `ui/theme/`.
 *
 * This is task 3.7's requirement — catch a colour defined for only one theme —
 * enforced at the source rather than by a screenshot diff. `SakuColorsTest` already
 * proves both token sets are complete and that no token is byte-identical across
 * them, which covers a token missing from one theme. What it cannot see is a
 * component writing `Color(0xFF...)` or `Color.White` inline, which is a colour
 * defined for *both* themes at once and therefore wrong in one of them.
 *
 * That was a real bug, not a hypothetical: the keypad's Save button and the add
 * button both used `Color.White` on the accent. The accent is #1B5AD9 in light but
 * #6C9BF0 in dark, where white text sits at roughly 2.6:1.
 *
 * A golden-image harness would catch the same class of bug at the cost of a plugin,
 * a Robolectric graphics mode and a directory of PNGs to re-record on every
 * intentional change. This costs one file and cannot flake.
 */
class NoHardCodedColorsTest {

    private val uiRoot = File("src/main/java/com/dvnnfrr/saku/ui")

    /** Only the token file and the Material bridge beside it may name a colour. */
    private val allowedDirectory = "theme"

    private val literal = Regex(
        """Color\(0x|Color\.(White|Black|Red|Green|Blue|Yellow|Cyan|Magenta|Gray|LightGray|DarkGray)\b""",
    )

    @Test
    fun `no colour literal outside the theme package`() {
        assertTrue("ui source root not found at ${uiRoot.absolutePath}", uiRoot.isDirectory)

        val offenders = uiRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { it.parentFile.name == allowedDirectory }
            .flatMap { file ->
                file.readLines().withIndex()
                    .filter { (_, line) -> literal.containsMatchIn(line) }
                    .map { (index, line) -> "${file.name}:${index + 1}  ${line.trim()}" }
            }
            .toList()

        assertTrue(
            buildString {
                appendLine("Colour literals outside ui/theme/:")
                offenders.forEach { appendLine("  $it") }
                appendLine()
                appendLine("Take the colour from SakuTheme.colors, or from")
                appendLine("MaterialTheme.colorScheme where the Material bridge already")
                appendLine("resolves it per theme (onPrimary, onError, scrim).")
            },
            offenders.isEmpty(),
        )
    }
}
