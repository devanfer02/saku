package com.dvnnfrr.saku.ui.firstrun

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.dvnnfrr.saku.R
import com.dvnnfrr.saku.ui.shared.ScreenHeader
import com.dvnnfrr.saku.ui.shell.MoreEntry
import com.dvnnfrr.saku.ui.theme.SakuPreview
import com.dvnnfrr.saku.ui.theme.SakuPreviewSurface
import com.dvnnfrr.saku.ui.theme.SakuTheme

/**
 * The first screen of a fresh install: where the user's money history lives.
 *
 * Both imports are listed above "Set up manually" on purpose. Every seeded account still
 * opens at the epoch until setup moves it to today, so history imported first counts in
 * full; history imported after a manual setup feeds Trans and Stats but not the balances
 * the user stated, and an older transfer is refused. See the change's design.md.
 *
 * Stateless: the host decides where each choice goes.
 */
@Composable
fun WelcomeScreen(
    modifier: Modifier = Modifier,
    onImportFromSaku: () -> Unit = {},
    onImportFromMoneyManager: () -> Unit = {},
    onSetUpManually: () -> Unit = {},
    onSkip: () -> Unit = {},
) {
    val colors = SakuTheme.colors
    val spacing = SakuTheme.spacing

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.screen, vertical = spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        Text(
            text = stringResource(R.string.first_run_title),
            style = SakuTheme.typography.display,
            color = colors.ink,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.first_run_intro),
            style = SakuTheme.typography.body,
            color = colors.ink2,
        )

        ChoiceList(
            choices = listOf(
                Choice(
                    icon = Icons.Filled.Restore,
                    title = stringResource(R.string.first_run_from_saku),
                    subtitle = stringResource(R.string.first_run_from_saku_subtitle),
                    onClick = onImportFromSaku,
                ),
                Choice(
                    icon = MoreEntry.IMPORT_MONEY_MANAGER.icon,
                    title = stringResource(MoreEntry.IMPORT_MONEY_MANAGER.labelRes),
                    subtitle = stringResource(MoreEntry.IMPORT_MONEY_MANAGER.subtitleRes),
                    onClick = onImportFromMoneyManager,
                ),
                Choice(
                    icon = Icons.Filled.Edit,
                    title = stringResource(R.string.first_run_manual),
                    subtitle = stringResource(R.string.first_run_manual_subtitle),
                    onClick = onSetUpManually,
                ),
            ),
            modifier = Modifier.padding(top = spacing.sm),
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TextButton(onClick = onSkip, modifier = Modifier.heightIn(min = spacing.touchTarget)) {
                Text(
                    text = stringResource(R.string.first_run_skip),
                    style = SakuTheme.typography.bodyStrong,
                    color = colors.accent,
                )
            }
            Text(
                text = stringResource(R.string.first_run_skip_hint),
                style = SakuTheme.typography.caption,
                color = colors.ink2,
            )
        }
    }
}

/**
 * Import from Saku: the three ways a Saku file comes in, each opening the screen More
 * opens. Labels, subtitles and icons are More's own, so the two lists cannot drift.
 */
@Composable
fun SakuImportChooser(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onOpen: (MoreEntry) -> Unit = {},
) {
    val spacing = SakuTheme.spacing
    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader(
            title = stringResource(R.string.first_run_saku_title),
            onBack = onBack,
            backLabel = stringResource(R.string.screen_back),
        )
        ChoiceList(
            choices = SAKU_IMPORTS.map { entry ->
                Choice(
                    icon = entry.icon,
                    title = stringResource(entry.labelRes),
                    subtitle = stringResource(entry.subtitleRes),
                    onClick = { onOpen(entry) },
                )
            },
            modifier = Modifier.padding(horizontal = spacing.screen, vertical = spacing.sm),
        )
    }
}

/** What Import from Saku offers, in the order More lists them. */
val SAKU_IMPORTS: List<MoreEntry> =
    listOf(MoreEntry.RESTORE, MoreEntry.IMPORT_DATABASE, MoreEntry.IMPORT_EXCEL)

private data class Choice(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val onClick: () -> Unit,
)

/** One grouped surface of rows, like the More menu; a list, not a stack of cards. */
@Composable
private fun ChoiceList(choices: List<Choice>, modifier: Modifier = Modifier) {
    val colors = SakuTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SakuTheme.spacing.corner))
            .background(colors.surface),
    ) {
        choices.forEachIndexed { index, choice ->
            ListItem(
                headlineContent = {
                    Text(choice.title, style = SakuTheme.typography.bodyStrong, color = colors.ink)
                },
                supportingContent = {
                    Text(choice.subtitle, style = SakuTheme.typography.caption, color = colors.ink2)
                },
                leadingContent = { Icon(choice.icon, contentDescription = null, tint = colors.accent) },
                trailingContent = {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = colors.ink3,
                    )
                },
                colors = ListItemDefaults.colors(containerColor = colors.surface),
                modifier = Modifier
                    .heightIn(min = SakuTheme.spacing.rowMinHeight)
                    .clickable(onClick = choice.onClick),
            )
            if (index != choices.lastIndex) HorizontalDivider(color = colors.line)
        }
    }
}

@SakuPreview
@Composable
private fun WelcomeScreenPreview() {
    SakuPreviewSurface(padded = false) { WelcomeScreen() }
}

@SakuPreview
@Composable
private fun SakuImportChooserPreview() {
    SakuPreviewSurface(padded = false) { SakuImportChooser() }
}
