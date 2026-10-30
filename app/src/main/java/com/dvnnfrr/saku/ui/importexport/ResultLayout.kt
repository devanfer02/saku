package com.dvnnfrr.saku.ui.importexport

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.dvnnfrr.saku.R
import com.dvnnfrr.saku.ui.shared.ThumbZonePane
import com.dvnnfrr.saku.ui.theme.SakuTheme

/**
 * A finished import, restore, backup or export: what happened, centred in the space under
 * the header, and one full-width Continue pinned at the bottom.
 *
 * A short result sits in the middle rather than under the header with half the screen
 * empty below it; a long one (an import naming many categories) scrolls from the top as
 * any page does. Continue lives in the bottom bar the previews use, so the thumb finds it
 * where the confirm button just was. Secondary actions, such as Import history, go in
 * [content] as [LinkButton]s.
 */
@Composable
internal fun ColumnScope.ResultLayout(
    onContinue: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    ThumbZonePane(modifier = Modifier.weight(1f)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(SakuTheme.spacing.lg),
            content = content,
        )
    }
    BottomActionBar {
        PrimaryButton(
            label = stringResource(R.string.action_continue),
            onClick = onContinue,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = SakuTheme.spacing.touchTarget)
                .testTag(RESULT_CONTINUE_TAG),
        )
    }
}

/** The result's first line: what was done, in the income colour, read as a heading. */
@Composable
internal fun ResultHeadline(text: String) {
    Text(
        text = text,
        style = SakuTheme.typography.bodyStrong,
        color = SakuTheme.colors.income,
        modifier = Modifier.fillMaxWidth().semantics { heading() },
    )
}

/** A secondary action on a result or a preview: accent text, no container. */
@Composable
internal fun LinkButton(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(
            text = text,
            color = SakuTheme.colors.accent,
            style = SakuTheme.typography.bodyStrong,
        )
    }
}

/** For instrumentation: the Continue button on every result screen. */
const val RESULT_CONTINUE_TAG = "result_continue"
