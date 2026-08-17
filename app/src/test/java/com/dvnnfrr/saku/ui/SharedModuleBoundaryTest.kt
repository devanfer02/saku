package com.dvnnfrr.saku.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * `ui/theme/` and `ui/shared/` are meant to survive being lifted into their own Gradle
 * module with nothing but a package rename. That only stays true if nothing in them
 * reaches upwards, so the rule is checked rather than asserted in a comment.
 *
 * String resources are on the forbidden list for the same reason as repositories: a
 * component that reaches for `R.string` cannot be reused with different copy, so every
 * label arrives as a parameter.
 */
class SharedModuleBoundaryTest {

    private val allowedImportPrefixes = listOf(
        "androidx.compose.",
        "androidx.annotation.",
        "android.content.res.Configuration",
        "com.dvnnfrr.saku.domain.",
        "com.dvnnfrr.saku.ui.theme.",
        "com.dvnnfrr.saku.ui.shared.",
        "kotlinx.datetime.",
        "kotlin.",
        "java.",
    )

    private fun sourceRoot(): File {
        val module = File(requireNotNull(System.getProperty("user.dir")))
        val root = File(module, "src/main/java/com/dvnnfrr/saku/ui")
        assertTrue("Could not locate ${root.absolutePath}", root.isDirectory)
        return root
    }

    private fun candidateFiles(): List<File> {
        val root = sourceRoot()
        return listOf("theme", "shared")
            .map { File(root, it) }
            .filter { it.isDirectory }
            .flatMap { it.walkTopDown().filter { file -> file.extension == "kt" } }
    }

    @Test
    fun `the design system and the shared components exist`() {
        val files = candidateFiles()
        assertTrue("No Kotlin sources found under ui/theme or ui/shared", files.isNotEmpty())
    }

    @Test
    fun `nothing in the design system or shared components imports from above it`() {
        val violations = mutableListOf<String>()
        for (file in candidateFiles()) {
            file.forEachLine { line ->
                val trimmed = line.trim()
                if (!trimmed.startsWith("import ")) return@forEachLine
                val imported = trimmed.removePrefix("import ").substringBefore(" as ").trim()
                if (allowedImportPrefixes.none { imported.startsWith(it) }) {
                    violations += "${file.name}: $imported"
                }
            }
        }
        assertTrue(
            "These imports would not survive extraction to a ui-core module:\n" +
                violations.joinToString("\n"),
            violations.isEmpty(),
        )
    }

    @Test
    fun `no shared component reaches for a string resource`() {
        val offenders = candidateFiles().filter { file ->
            val text = file.readText()
            text.contains("R.string") || text.contains("stringResource")
        }
        assertTrue(
            "Copy must arrive as a parameter: ${offenders.map { it.name }}",
            offenders.isEmpty(),
        )
    }

    @Test
    fun `no shared component reaches for a repository, container or view model`() {
        val forbidden = listOf("Repository", "AppContainer", "ViewModel", "viewModel(")
        val offenders = mutableListOf<String>()
        for (file in candidateFiles()) {
            val text = file.readText()
            forbidden.filter { text.contains(it) }.forEach { offenders += "${file.name}: $it" }
        }
        assertTrue(
            "Shared components take data as parameters:\n${offenders.joinToString("\n")}",
            offenders.isEmpty(),
        )
    }
}
