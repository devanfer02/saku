package com.dvnnfrr.saku.ui.importexport

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dvnnfrr.saku.R
import com.dvnnfrr.saku.ui.shared.ScreenHeader
import com.dvnnfrr.saku.ui.theme.SakuTheme

/**
 * Export, through the system file picker and nowhere else.
 *
 * `ACTION_CREATE_DOCUMENT` is what makes that literal rather than a claim: the app never
 * learns a path, only a document the user created, and it holds no permission that would
 * let a file leave the device. The line saying so is on the screen because "no upload" is
 * a promise worth making visible.
 */
@Composable
fun ExportScreen(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    viewModel: ExportViewModel = viewModel(factory = ExportViewModel.Factory),
) {
    val state by viewModel.state.collectAsState()
    val resolver = LocalContext.current.contentResolver
    val defaultName = stringResource(R.string.export_default_file_name)

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(XLSX_MIME_TYPE),
    ) { uri ->
        uri?.let { viewModel.export(ContentDocumentSink(resolver, it)) }
    }

    ExportScreenBody(
        state = state,
        modifier = modifier,
        onBack = onBack,
        onChooseDestination = { picker.launch("$defaultName.xlsx") },
    )
}

@Composable
fun ExportScreenBody(
    state: ExportUiState,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onChooseDestination: () -> Unit = {},
) {
    val colors = SakuTheme.colors
    val spacing = SakuTheme.spacing

    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        ScreenHeader(title = stringResource(R.string.more_export_excel), onBack = onBack, backLabel = stringResource(R.string.screen_back))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(spacing.screen),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            Text(
                text = stringResource(R.string.export_intro),
                style = SakuTheme.typography.body,
                color = colors.ink2,
            )

            Button(
                onClick = onChooseDestination,
                enabled = state !is ExportUiState.Writing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = colors.surfaceAlt,
                    disabledContentColor = colors.inkDisabled,
                ),
                modifier = Modifier.fillMaxWidth().testTag(EXPORT_ACTION_TAG),
            ) {
                Text(
                    text = stringResource(R.string.export_action),
                    style = SakuTheme.typography.bodyStrong,
                )
            }

            when (state) {
                ExportUiState.Idle -> Unit

                is ExportUiState.Writing -> Column(
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    CircularProgressIndicator(color = colors.accent)
                    Text(
                        text = stringResource(R.string.export_writing, state.fileName),
                        style = SakuTheme.typography.body,
                        color = colors.ink2,
                    )
                }

                is ExportUiState.Written -> Text(
                    text = stringResource(
                        R.string.export_done,
                        state.transactionCount,
                        state.fileName,
                    ),
                    style = SakuTheme.typography.bodyStrong,
                    color = colors.income,
                )

                is ExportUiState.Failed -> Column(
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    Text(
                        text = stringResource(R.string.export_failed, state.fileName),
                        style = SakuTheme.typography.bodyStrong,
                        color = colors.expense,
                    )
                    state.detail?.let {
                        Text(text = it, style = SakuTheme.typography.caption, color = colors.ink2)
                    }
                }
            }

            Text(
                text = stringResource(R.string.export_local_only),
                style = SakuTheme.typography.caption,
                color = colors.ink2,
            )
        }
    }
}

/** For instrumentation. See the note beside IMPORT_ACTION_TAG. */
const val EXPORT_ACTION_TAG = "export_choose_destination"
