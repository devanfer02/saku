package com.dvnnfrr.saku.ui.shared

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit

/** Above this font scale, labels in fixed-size slots stop growing. */
const val SLOT_LABEL_MAX_SCALE = 1.3f

/**
 * [size] as it would render at no more than [SLOT_LABEL_MAX_SCALE] font scale. For a label
 * inside a slot whose width does not grow with the font (a bottom-bar item, a tab, a ring),
 * where 200% would break the word or spill out. Body text never uses this.
 */
@Composable
fun cappedFontSize(size: TextUnit): TextUnit {
    val scale = LocalDensity.current.fontScale
    return if (scale > SLOT_LABEL_MAX_SCALE) size * (SLOT_LABEL_MAX_SCALE / scale) else size
}

/** [style] with its font size passed through [cappedFontSize]. */
@Composable
fun TextStyle.cappedForSlot(): TextStyle = copy(fontSize = cappedFontSize(fontSize))
