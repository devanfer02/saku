package com.dvnnfrr.saku.ui.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dvnnfrr.saku.ui.theme.SakuPreview
import com.dvnnfrr.saku.ui.theme.SakuPreviewSurface
import com.dvnnfrr.saku.ui.theme.SakuTheme

/**
 * The space under a screen's header, laid out for a thumb.
 *
 * The owner holds the phone in one hand and the thumb reaches about half way up, so a
 * screen that waits on one choice sits that choice in the middle of the free space
 * rather than under the header. [verticalBias] moves the block within the space: 0 is
 * the centre, a positive value pushes it lower. It still scrolls when the content is
 * taller than the space, for a large font scale.
 *
 * Give it a bounded height, such as `Modifier.weight(1f)` inside a column. On a wide screen
 * the block keeps to one readable column in the middle.
 */
@Composable
fun ThumbZonePane(
    modifier: Modifier = Modifier,
    verticalBias: Float = 0f,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = SakuTheme.spacing
    BoxWithConstraints(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val minHeight = if (constraints.hasBoundedHeight) maxHeight else 0.dp
        Column(
            modifier = Modifier
                .widthIn(max = THUMB_ZONE_MAX_WIDTH)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = minHeight)
                .padding(horizontal = spacing.screen, vertical = spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.lg, BiasAlignment.Vertical(verticalBias)),
            content = content,
        )
    }
}

/**
 * One card for a screen that waits on one input: pick a file, or choose where to save.
 *
 * Title, one short line, a tinted drop zone holding the icon, and the filled action at the
 * card's foot, where the thumb already is. The zone is a second, larger tap target for the
 * same action; it is hidden from accessibility services because the button already says
 * what it does, and only its label (a file name while busy) is read out.
 *
 * [busy] swaps the icon for a spinner and disables both targets. [isError] paints the
 * message as a refusal. [details] sits between the message and the zone (the columns a
 * refused file was missing, say), and [notes] sits under the button (what happens to the
 * file once written). Every word arrives from the caller.
 */
@Composable
fun PickCard(
    title: String,
    message: String,
    icon: ImageVector,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    zoneLabel: String? = null,
    busy: Boolean = false,
    isError: Boolean = false,
    /** Applied to the button, so a test tag lands on the clickable it names. */
    actionModifier: Modifier = Modifier,
    details: @Composable ColumnScope.() -> Unit = {},
    notes: @Composable ColumnScope.() -> Unit = {},
) {
    val colors = SakuTheme.colors
    val spacing = SakuTheme.spacing
    val typography = SakuTheme.typography

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(spacing.corner + spacing.xs))
            .padding(spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Text(
            text = title,
            style = typography.heading,
            color = colors.ink,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = message,
            style = if (isError) typography.bodyStrong else typography.body,
            color = if (isError) colors.expense else colors.ink2,
        )
        details()
        DropZone(
            icon = icon,
            label = zoneLabel,
            busy = busy,
            onClick = onAction,
            modifier = Modifier.padding(vertical = spacing.xs),
        )
        Button(
            onClick = onAction,
            enabled = !busy,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.accent,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = colors.surfaceAlt,
                disabledContentColor = colors.inkDisabled,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = spacing.touchTarget)
                .then(actionModifier),
        ) {
            Text(text = actionLabel, style = typography.bodyStrong)
        }
        notes()
    }
}

@Composable
private fun DropZone(
    icon: ImageVector,
    label: String?,
    busy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SakuTheme.colors
    val spacing = SakuTheme.spacing
    val corner = spacing.corner
    val outline = colors.lineStrong

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = DROP_ZONE_MIN_HEIGHT)
            .clearAndSetSemantics { label?.let { contentDescription = it } }
            .clip(RoundedCornerShape(corner))
            .background(colors.accentWash)
            .drawBehind { dashedOutline(outline, corner) }
            .clickable(enabled = !busy, onClick = onClick)
            .padding(spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.sm, Alignment.CenterVertically),
    ) {
        if (busy) {
            CircularProgressIndicator(color = colors.accent, modifier = Modifier.size(DROP_ZONE_ICON))
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(DROP_ZONE_ICON),
            )
        }
        label?.let {
            Text(
                text = it,
                style = SakuTheme.typography.caption,
                color = colors.ink2,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun DrawScope.dashedOutline(
    color: Color,
    corner: Dp,
) {
    val stroke = 1.dp.toPx()
    val inset = stroke / 2
    drawRoundRect(
        color = color,
        topLeft = Offset(inset, inset),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(corner.toPx() - inset),
        style = Stroke(
            width = stroke,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
        ),
    )
}

/** Wide enough for a phone in landscape; on a tablet the block stays a readable column. */
private val THUMB_ZONE_MAX_WIDTH = 480.dp
private val DROP_ZONE_MIN_HEIGHT = 128.dp
private val DROP_ZONE_ICON = 40.dp

@SakuPreview
@Composable
private fun PickCardPreview() {
    SakuPreviewSurface(padded = false) {
        Column(modifier = Modifier.width(360.dp).height(640.dp)) {
            ThumbZonePane {
                PickCard(
                    title = "Pick a Saku backup",
                    message = "Adds only what this phone lacks. Nothing is deleted, and Import history can reverse it.",
                    icon = Icons.Outlined.UploadFile,
                    actionLabel = "Choose a backup",
                    onAction = {},
                    zoneLabel = "An .xlsx file",
                )
            }
        }
    }
}

@SakuPreview
@Composable
private fun PickCardBusyPreview() {
    SakuPreviewSurface {
        PickCard(
            title = "Save a backup",
            message = "Every account, category and transaction, in one .xlsx file.",
            icon = Icons.Outlined.FolderOpen,
            actionLabel = "Choose where to save",
            onAction = {},
            zoneLabel = "Writing Saku backup 2026-10-30.xlsx…",
            busy = true,
            notes = {
                Text(
                    text = "The file is not encrypted.",
                    style = SakuTheme.typography.caption,
                    color = SakuTheme.colors.ink2,
                )
            },
        )
    }
}

@SakuPreview
@Composable
private fun PickCardRefusedPreview() {
    SakuPreviewSurface {
        PickCard(
            title = "That file was not imported",
            message = "report.pdf is not an .xlsx file.",
            icon = Icons.Outlined.UploadFile,
            actionLabel = "Choose another file",
            onAction = {},
            isError = true,
        )
    }
}
