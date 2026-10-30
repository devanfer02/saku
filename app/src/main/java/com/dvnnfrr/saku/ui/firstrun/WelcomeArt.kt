package com.dvnnfrr.saku.ui.firstrun

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.dvnnfrr.saku.ui.theme.SakuColors
import com.dvnnfrr.saku.ui.theme.SakuPreview
import com.dvnnfrr.saku.ui.theme.SakuPreviewSurface
import com.dvnnfrr.saku.ui.theme.SakuTheme

/**
 * The welcome screen's illustration: a wallet like the launcher icon with two cards
 * tucked in, a receipt, a stack of coins, a small house and a rising bar chart, on soft
 * washes of blue, yellow and red.
 *
 * Decoration only. It says nothing the text does not, so it is hidden from accessibility
 * services. It draws inside whatever box it is given, scaled to fit and centred, and
 * draws nothing in a box too short to show it, so a large font scale that squeezes the
 * free space simply drops it. Every colour is a theme token; see [SakuColors.artBlue].
 */
@Composable
fun WelcomeArt(modifier: Modifier = Modifier) {
    val colors = SakuTheme.colors
    Canvas(modifier = modifier.clearAndSetSemantics {}) {
        if (size.height < MIN_HEIGHT.toPx() || size.width <= 0f) return@Canvas
        val factor = minOf(size.width / ART_WIDTH, size.height / ART_HEIGHT)
        val left = (size.width - ART_WIDTH * factor) / 2f
        val top = (size.height - ART_HEIGHT * factor) / 2f
        withTransform({
            translate(left, top)
            scale(factor, factor, pivot = Offset.Zero)
        }) {
            drawArt(colors)
        }
    }
}

/** The design box, in units. Shapes below are placed in it, then scaled to fit. */
private const val ART_WIDTH = 320f
private const val ART_HEIGHT = 200f

/** Under this the shapes are too small to read as anything, so none are drawn. */
private val MIN_HEIGHT = 72.dp

private fun DrawScope.drawArt(colors: SakuColors) {
    // Washes first: the soft ground the motifs sit on.
    drawCircle(colors.artBlueWash, radius = 84f, center = Offset(160f, 112f))
    drawCircle(colors.artYellowWash, radius = 40f, center = Offset(74f, 62f))
    drawCircle(colors.artRedWash, radius = 32f, center = Offset(262f, 56f))

    // A few loose dots, so the group does not sit as three discs.
    drawCircle(colors.artYellow, radius = 5f, center = Offset(30f, 112f))
    drawCircle(colors.artBlue, radius = 4f, center = Offset(302f, 122f))
    drawCircle(colors.artRed, radius = 4f, center = Offset(212f, 22f))
    drawCircle(colors.artBlue, radius = 3f, center = Offset(124f, 20f))

    drawReceipt(colors)
    drawHouse(colors)
    drawWallet(colors)
    drawCoins(colors)
    drawChart(colors)
}

/** A paper receipt, tilted, with a torn edge and a few printed lines. */
private fun DrawScope.drawReceipt(colors: SakuColors) {
    val left = 72f
    val right = 124f
    val top = 46f
    val bottom = 140f
    rotate(degrees = -8f, pivot = Offset(98f, 94f)) {
        val paper = Path().apply {
            addRoundRect(
                RoundRect(
                    left = left,
                    top = top,
                    right = right,
                    bottom = bottom,
                    topLeftCornerRadius = CornerRadius(6f),
                    topRightCornerRadius = CornerRadius(6f),
                    bottomRightCornerRadius = CornerRadius.Zero,
                    bottomLeftCornerRadius = CornerRadius.Zero,
                ),
            )
        }
        // The torn foot: a row of small teeth hanging under the paper.
        val teeth = 6
        val step = (right - left) / teeth
        val edge = Path().apply {
            moveTo(left, bottom)
            for (i in 0 until teeth) {
                lineTo(left + step * i + step / 2f, bottom + 5f)
                lineTo(left + step * (i + 1), bottom)
            }
            close()
        }
        drawPath(paper, colors.surface)
        drawPath(edge, colors.surface)
        drawRect(colors.artBlue, topLeft = Offset(left + 8f, top + 10f), size = Size(20f, 6f))
        // Printed lines: the offset from the top, and where each line ends.
        listOf(32f to 112f, 44f to 100f, 56f to 108f, 68f to 94f).forEach { (dy, endX) ->
            drawLine(
                colors.lineStrong,
                start = Offset(left + 8f, top + dy),
                end = Offset(endX, top + dy),
                strokeWidth = 3f,
                cap = StrokeCap.Round,
            )
        }
        drawLine(
            colors.ink3,
            start = Offset(left + 26f, top + 82f),
            end = Offset(right - 8f, top + 82f),
            strokeWidth = 4f,
            cap = StrokeCap.Round,
        )
    }
}

/** A small house on the red wash: home, the life the money is for. */
private fun DrawScope.drawHouse(colors: SakuColors) {
    drawRoundRect(
        colors.surface,
        topLeft = Offset(248f, 54f),
        size = Size(28f, 22f),
        cornerRadius = CornerRadius(2f),
    )
    val roof = Path().apply {
        moveTo(242f, 57f)
        lineTo(262f, 38f)
        lineTo(282f, 57f)
        close()
    }
    drawPath(roof, colors.artRed)
    drawRoundRect(
        colors.artRed,
        topLeft = Offset(258f, 63f),
        size = Size(8f, 13f),
        cornerRadius = CornerRadius(2f),
    )
}

/** The launcher icon's wallet, with a red and a yellow card tucked in behind. */
private fun DrawScope.drawWallet(colors: SakuColors) {
    rotate(degrees = -16f, pivot = Offset(170f, 84f)) {
        drawRoundRect(
            colors.artRed,
            topLeft = Offset(140f, 66f),
            size = Size(60f, 34f),
            cornerRadius = CornerRadius(6f),
        )
    }
    rotate(degrees = -6f, pivot = Offset(165f, 86f)) {
        drawRoundRect(
            colors.artYellow,
            topLeft = Offset(132f, 70f),
            size = Size(64f, 34f),
            cornerRadius = CornerRadius(6f),
        )
    }
    drawRoundRect(
        colors.artBlue,
        topLeft = Offset(118f, 86f),
        size = Size(104f, 68f),
        cornerRadius = CornerRadius(14f),
    )
    // The clasp pocket, flush with the wallet's right edge as on the icon.
    val pocket = Path().apply {
        addRoundRect(
            RoundRect(
                left = 184f,
                top = 106f,
                right = 222f,
                bottom = 134f,
                topLeftCornerRadius = CornerRadius(10f),
                bottomLeftCornerRadius = CornerRadius(10f),
                topRightCornerRadius = CornerRadius.Zero,
                bottomRightCornerRadius = CornerRadius.Zero,
            ),
        )
    }
    drawPath(pocket, colors.artBlueWash)
    drawCircle(colors.artBlue, radius = 4.5f, center = Offset(200f, 120f))
}

/** A short stack of coins with one standing on top. */
private fun DrawScope.drawCoins(colors: SakuColors) {
    listOf(142f, 128f, 114f).forEach { top ->
        drawRoundRect(
            colors.artYellow,
            topLeft = Offset(236f, top),
            size = Size(46f, 12f),
            cornerRadius = CornerRadius(6f),
        )
    }
    drawCircle(colors.artYellow, radius = 15f, center = Offset(259f, 96f))
    drawCircle(
        colors.artYellowWash,
        radius = 9.5f,
        center = Offset(259f, 96f),
        style = Stroke(width = 2.5f),
    )
}

/** Three rising bars on a baseline, one in each hue. */
private fun DrawScope.drawChart(colors: SakuColors) {
    val baseline = 170f
    listOf(
        Triple(24f, 14f, colors.artRed),
        Triple(36f, 24f, colors.artYellow),
        Triple(48f, 36f, colors.artBlue),
    ).forEach { (x, height, color) -> drawBar(x, baseline, height, color) }
    drawLine(
        colors.lineStrong,
        start = Offset(18f, baseline + 3f),
        end = Offset(62f, baseline + 3f),
        strokeWidth = 2.5f,
        cap = StrokeCap.Round,
    )
}

private fun DrawScope.drawBar(x: Float, baseline: Float, height: Float, color: Color) {
    drawRoundRect(
        color,
        topLeft = Offset(x, baseline - height),
        size = Size(8f, height),
        cornerRadius = CornerRadius(3f),
    )
}

@SakuPreview
@Composable
private fun WelcomeArtPreview() {
    SakuPreviewSurface {
        WelcomeArt(modifier = Modifier.fillMaxWidth().height(220.dp))
    }
}
