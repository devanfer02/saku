package com.dvnnfrr.saku.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The spacing scale, plus the handful of fixed dimensions that more than one screen has
 * to agree on: row height, touch target, swatch and corner.
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
)

val SakuSpacingDefaults = SakuSpacing()
