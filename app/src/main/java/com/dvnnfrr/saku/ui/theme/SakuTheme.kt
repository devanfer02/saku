package com.dvnnfrr.saku.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.dvnnfrr.saku.domain.ThemePreference

private val LocalSakuColors = staticCompositionLocalOf<SakuColors> {
    error("No SakuColors in scope. Wrap the tree in SakuTheme { }.")
}

private val LocalSakuTypography = staticCompositionLocalOf { SakuTypographyDefaults }

private val LocalSakuSpacing = staticCompositionLocalOf { SakuSpacingDefaults }

@Composable
fun SakuTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkSakuColors else LightSakuColors
    CompositionLocalProvider(
        LocalSakuColors provides colors,
        LocalSakuTypography provides SakuTypographyDefaults,
        LocalSakuSpacing provides SakuSpacingDefaults,
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterialColorScheme(darkTheme),
            typography = SakuTypographyDefaults.toMaterialTypography(),
            content = content,
        )
    }
}

object SakuTheme {
    val colors: SakuColors
        @Composable @ReadOnlyComposable get() = LocalSakuColors.current

    val typography: SakuTypography
        @Composable @ReadOnlyComposable get() = LocalSakuTypography.current

    val spacing: SakuSpacing
        @Composable @ReadOnlyComposable get() = LocalSakuSpacing.current
}

/**
 * Resolves the appearance setting against the device. Pure enough to call from anywhere
 * the preference is known; the persisted value itself lives outside the theme layer.
 */
fun ThemePreference.resolveDarkTheme(systemInDark: Boolean): Boolean = when (this) {
    ThemePreference.FOLLOW_SYSTEM -> systemInDark
    ThemePreference.LIGHT -> false
    ThemePreference.DARK -> true
}

@Composable
fun SakuTheme(
    preference: ThemePreference,
    content: @Composable () -> Unit,
) {
    SakuTheme(
        darkTheme = preference.resolveDarkTheme(isSystemInDarkTheme()),
        content = content,
    )
}

/**
 * Material 3 components read their colours from [MaterialTheme], so the Saku tokens are
 * projected onto a colour scheme rather than left beside one. Anything Material paints
 * for us — a ripple, a dialog scrim, a text field outline — then lands on the same
 * palette as everything hand-drawn.
 */
private fun SakuColors.toMaterialColorScheme(darkTheme: Boolean) = if (darkTheme) {
    darkColorScheme(
        primary = accent,
        onPrimary = Color(0xFF0A0F1E),
        primaryContainer = accentWash,
        onPrimaryContainer = accent,
        secondary = ink2,
        onSecondary = background,
        secondaryContainer = surfaceAlt,
        onSecondaryContainer = ink,
        tertiary = accent,
        onTertiary = Color(0xFF0A0F1E),
        background = background,
        onBackground = ink,
        surface = surface,
        onSurface = ink,
        surfaceVariant = surfaceAlt,
        onSurfaceVariant = ink2,
        surfaceContainer = surface,
        surfaceContainerHigh = surfaceAlt,
        surfaceContainerHighest = surfaceAlt,
        surfaceContainerLow = surface,
        surfaceContainerLowest = background,
        outline = line,
        outlineVariant = line,
        error = expense,
        onError = Color(0xFF0A0F1E),
        errorContainer = surfaceAlt,
        onErrorContainer = expense,
        scrim = Color(0xFF000000),
    )
} else {
    lightColorScheme(
        primary = accent,
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = accentWash,
        onPrimaryContainer = accent,
        secondary = ink2,
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = surfaceAlt,
        onSecondaryContainer = ink,
        tertiary = accent,
        onTertiary = Color(0xFFFFFFFF),
        background = background,
        onBackground = ink,
        surface = surface,
        onSurface = ink,
        surfaceVariant = surfaceAlt,
        onSurfaceVariant = ink2,
        surfaceContainer = surface,
        surfaceContainerHigh = surfaceAlt,
        surfaceContainerHighest = surfaceAlt,
        surfaceContainerLow = surface,
        surfaceContainerLowest = background,
        outline = line,
        outlineVariant = line,
        error = expense,
        onError = Color(0xFFFFFFFF),
        errorContainer = accentWash,
        onErrorContainer = expense,
        scrim = Color(0xFF000000),
    )
}

private fun SakuTypography.toMaterialTypography(): Typography {
    val base = Typography()
    return base.copy(
        displaySmall = display,
        headlineSmall = title,
        titleLarge = title,
        titleMedium = heading,
        titleSmall = bodyStrong,
        bodyLarge = body,
        bodyMedium = body,
        bodySmall = caption,
        labelLarge = label,
        labelMedium = label,
        labelSmall = caption,
    )
}
