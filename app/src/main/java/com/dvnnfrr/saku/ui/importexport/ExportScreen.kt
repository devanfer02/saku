package com.dvnnfrr.saku.ui.importexport

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
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
import com.dvnnfrr.saku.ui.shared.PickCard
import com.dvnnfrr.saku.ui.shared.ScreenHeader
import com.dvnnfrr.saku.ui.shared.ThumbZonePane
import com.dvnnfrr.saku.ui.theme.SakuPreview
import com.dvnnfrr.saku.ui.theme.SakuPreviewSurface
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

    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        ScreenHeader(title = stringResource(R.string.more_export_excel), onBack = onBack, backLabel = stringResource(R.string.screen_back))
        ThumbZonePane(modifier = Modifier.weight(1f)) {
            PickCard(
                title = stringResource(R.string.export_pick_title),
                message = stringResource(R.string.export_intro),
                icon = Icons.Outlined.FolderOpen,
                actionLabel = stringResource(R.string.export_action),
                onAction = onChooseDestination,
                zoneLabel = if (state is ExportUiState.Writing) {
                    stringResource(R.string.export_writing, state.fileName)
                } else {
                    stringResource(R.string.export_pick_zone)
                },
                busy = state is ExportUiState.Writing,
                actionModifier = Modifier.testTag(EXPORT_ACTION_TAG),
                notes = { PrivacyNote(stringResource(R.string.export_local_only)) },
            )

            when (state) {
                ExportUiState.Idle, is ExportUiState.Writing -> Unit

                is ExportUiState.Written -> Text(
                    text = stringResource(
                        R.string.export_done,
                        state.transactionCount,
                        state.fileName,
                    ),
                    style = SakuTheme.typography.bodyStrong,
                    color = colors.income,
                    modifier = Modifier.fillMaxWidth(),
                )

                is ExportUiState.Failed -> WriteFailure(
                    message = stringResource(R.string.export_failed, state.fileName),
                    detail = state.detail,
                )
            }
        }
    }
}

/** For instrumentation. See the note beside IMPORT_ACTION_TAG. */
const val EXPORT_ACTION_TAG = "export_choose_destination"

@SakuPreview
@Composable
private fun ExportIdlePreview() {
    SakuPreviewSurface(padded = false) { ExportScreenBody(state = ExportUiState.Idle) }
}

@SakuPreview
@Composable
private fun ExportWrittenPreview() {
    SakuPreviewSurface(padded = false) {
        ExportScreenBody(state = ExportUiState.Written(transactionCount = 1_300, fileName = "saku-export.xlsx"))
    }
}
