package com.dvnnfrr.saku.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The spacing scale, plus the handful of fixed dimensions that more than one screen has
 * to agree on. [donutSegmentGap] lives here because the gap is a requirement, not a
 * styling choice: it is the second cue that stops a donut conveying identity by colour
 * alone, so every donut in the app reads it from the same token.
 */
@Immutable
data class SakuSpacing(
    val none: Dp = 0.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    val screen: Dp = 16.dp,
    val rowMinHeight: Dp = 56.dp,
    val touchTarget: Dp = 48.dp,
    val swatch: Dp = 10.dp,
    val corner: Dp = 12.dp,
    val donutSize: Dp = 176.dp,
    val donutStroke: Dp = 18.dp,
    val donutSegmentGap: Dp = 2.dp,
)

val SakuSpacingDefaults = SakuSpacing()
