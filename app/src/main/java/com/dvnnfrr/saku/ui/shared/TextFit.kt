package com.dvnnfrr.saku.ui.shared

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.isSpecified

/**
 * The factor a font size is multiplied by so a line [measuredWidthPx] wide fits into
 * [availableWidthPx]. Never above 1: text that fits keeps its size, and text that does
 * not shrinks rather than wrapping.
 */
internal fun textFitScale(measuredWidthPx: Float, availableWidthPx: Float): Float {
    if (measuredWidthPx <= availableWidthPx || measuredWidthPx <= 0f) return 1f
    return (availableWidthPx / measuredWidthPx).coerceAtLeast(0f)
}

/**
 * [style] shrunk until [text] fits on one line [maxWidthPx] wide. Measured again after
 * each step because glyph widths round to whole pixels and do not scale exactly, and each
 * step aims a little under the width: aiming at it exactly left a long amount a few
 * pixels over, its last digit clipped.
 */
internal fun TextMeasurer.fitOnOneLine(text: String, style: TextStyle, maxWidthPx: Float): TextStyle {
    var fitted = style
    repeat(FIT_ATTEMPTS) {
        val width = measure(text, fitted, softWrap = false, maxLines = 1).size.width.toFloat()
        val scale = textFitScale(width, maxWidthPx)
        if (scale >= 1f) return fitted
        val step = scale * FIT_SLACK
        fitted = fitted.copy(
            fontSize = fitted.fontSize * step,
            lineHeight = if (fitted.lineHeight.isSpecified) fitted.lineHeight * step else fitted.lineHeight,
        )
    }
    return fitted
}

private const val FIT_ATTEMPTS = 4
private const val FIT_SLACK = 0.97f
