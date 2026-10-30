package com.dvnnfrr.saku.ui.importexport

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dvnnfrr.saku.R
import com.dvnnfrr.saku.ui.shared.AccountPicker
import com.dvnnfrr.saku.ui.shared.EmptyState
import com.dvnnfrr.saku.ui.theme.SakuTheme
import com.dvnnfrr.saku.ui.theme.formatIdr

/**
 * The import screen: pick a file, check what it will do, confirm.
 *
 * The preview is the product here, not a courtesy. A year of history arriving
 * in one tap is unreviewable afterwards, so everything that could be wrong — which
 * account a name maps to, where an investment transfer lands, how many rows are already
 * present — is settled before a single row is written.
 */
@Composable
fun ImportScreen(
    entryPoint: ImportEntryPoint,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onViewTransactions: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onDone: () -> Unit = {},
    viewModel: ImportViewModel = viewModel(factory = ImportViewModel.factory(entryPoint)),
) {
    val state by viewModel.state.collectAsState()
    val resolver = LocalContext.current.contentResolver

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        // Null means the user backed out of the system picker, which is not an error.
        uri?.let { viewModel.load(ContentDocumentSource(resolver, it)) }
    }

    ImportScreenBody(
        state = state,
        modifier = modifier,
        onPickFile = { picker.launch(SPREADSHEET_MIME_TYPES) },
        onChooseAccount = viewModel::chooseAccount,
        onChooseDestination = viewModel::chooseTransferDestination,
        onSetIncluded = viewModel::setDiscrepancyIncluded,
        onConfirm = viewModel::confirm,
        onDiscard = viewModel::discard,
        onBack = onBack,
        onViewTransactions = onViewTransactions,
        onOpenHistory = onOpenHistory,
        onDone = onDone,
    )
}

/** Stateless, so every branch can be previewed and driven from a test. */
@Composable
fun ImportScreenBody(
    state: ImportUiState,
    modifier: Modifier = Modifier,
    onPickFile: () -> Unit = {},
    onChooseAccount: (String, AccountChoice) -> Unit = { _, _ -> },
    onChooseDestination: (String?, AccountChoice) -> Unit = { _, _ -> },
    onSetIncluded: (Int, Boolean) -> Unit = { _, _ -> },
    onConfirm: () -> Unit = {},
    onDiscard: () -> Unit = {},
    /** Null hides the back arrow, for a host with its own way out. */
    onBack: (() -> Unit)? = null,
    onViewTransactions: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onDone: () -> Unit = {},
) {
    val colors = SakuTheme.colors

    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        Header(title = stringResource(state.entryPoint.titleRes()), onBack = onBack)
        when (state) {
            is ImportUiState.Idle -> IdleBody(state.entryPoint, onPickFile)

            is ImportUiState.Reading -> Centered {
                CircularProgressIndicator(color = colors.accent)
                Text(
                    text = stringResource(R.string.import_reading, state.fileName),
                    style = SakuTheme.typography.body,
                    color = colors.ink2,
                )
            }

            is ImportUiState.Refused -> RefusedBody(state, onPickFile)

            is ImportUiState.Imported -> DoneBody(
                summary = state.summary,
                onViewTransactions = onViewTransactions,
                onOpenHistory = onOpenHistory,
                onPickFile = onPickFile,
                onDone = onDone,
            )

            is ImportUiState.Ready -> PreviewBody(
                preview = state.preview,
                onChooseAccount = onChooseAccount,
                onChooseDestination = onChooseDestination,
                onSetIncluded = onSetIncluded,
                onConfirm = onConfirm,
                onDiscard = onDiscard,
            )
        }
    }
}

@Composable
private fun IdleBody(entryPoint: ImportEntryPoint, onPickFile: () -> Unit) {
    EmptyState(
        icon = Icons.Outlined.FileUpload,
        message = stringResource(entryPoint.introRes()),
        modifier = Modifier.fillMaxWidth(),
        action = {
            TextButton(onClick = onPickFile) {
                Text(
                    text = stringResource(R.string.import_choose_file),
                    color = SakuTheme.colors.accent,
                    style = SakuTheme.typography.bodyStrong,
                )
            }
        },
    )
}

@Composable
private fun RefusedBody(state: ImportUiState.Refused, onPickFile: () -> Unit) {
    val spacing = SakuTheme.spacing
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(spacing.screen),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        SectionTitle(stringResource(R.string.import_refused_title))
        Body(state.reason.message(state.fileName))

        // Naming both column lists is the difference between "wrong file" and a user who
        // can see that one header was renamed.
        (state.reason as? ReadFailure.UnrecognisedLayout)?.let { layout ->
            val (expected, found) = layout.expectedAndFound()
            Caption(expected)
            Caption(found)
        }

        TextButton(onClick = onPickFile) {
            Text(
                text = stringResource(R.string.import_choose_another),
                color = SakuTheme.colors.accent,
                style = SakuTheme.typography.bodyStrong,
            )
        }
    }
}

/**
 * The result. It says what went in, by type and date and account, because "1300 added"
 * alone gives the user no way to tell whether a mapping landed where they meant, and
 * the obvious next step is to go and look at those transactions.
 */
@Composable
private fun ColumnScope.DoneBody(
    summary: ImportSummary,
    onViewTransactions: () -> Unit,
    onOpenHistory: () -> Unit,
    onPickFile: () -> Unit,
    onDone: () -> Unit,
) {
    val spacing = SakuTheme.spacing

    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.screen, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            SectionTitle(stringResource(R.string.import_done_title))
            Body(
                stringResource(
                    R.string.import_done_body,
                    summary.added,
                    summary.fileName,
                    summary.alreadyHere,
                ),
            )
        }

        if (summary.added > 0) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                SectionTitle(stringResource(R.string.import_done_added_title))
                Card {
                    summary.span?.let {
                        FactRow(
                            stringResource(R.string.import_preview_span),
                            stringResource(
                                R.string.import_preview_span_value,
                                it.first.readable(),
                                it.last.readable(),
                            ),
                        )
                    }
                    summary.byType.forEach { (type, count) ->
                        FactRow(stringResource(type.addedRes()), count.toString())
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                SectionTitle(stringResource(R.string.import_done_accounts))
                Body(summary.accounts.joinToString(", "))
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            SectionTitle(stringResource(R.string.import_done_categories))
            Body(
                if (summary.createdCategories.isEmpty()) {
                    stringResource(R.string.import_done_categories_none)
                } else {
                    summary.createdCategories.joinToString(", ")
                },
            )
        }

        Column {
            LinkButton(stringResource(R.string.more_import_history), onOpenHistory)
            LinkButton(stringResource(R.string.import_choose_another), onPickFile)
        }
    }

    HorizontalDivider(color = SakuTheme.colors.line)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SakuTheme.colors.surface)
            .padding(horizontal = spacing.screen, vertical = spacing.md),
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onDone) {
            Text(
                text = stringResource(R.string.action_done),
                style = SakuTheme.typography.bodyStrong,
                color = SakuTheme.colors.ink2,
            )
        }
        Button(
            onClick = onViewTransactions,
            colors = ButtonDefaults.buttonColors(
                containerColor = SakuTheme.colors.accent,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = stringResource(R.string.import_done_view_transactions),
                style = SakuTheme.typography.bodyStrong,
            )
        }
    }
}

@Composable
private fun LinkButton(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(
            text = text,
            color = SakuTheme.colors.accent,
            style = SakuTheme.typography.bodyStrong,
        )
    }
}

/** The title row, so the screen says where it is and has a way out besides system back. */
@Composable
private fun Header(title: String, onBack: (() -> Unit)?) {
    val spacing = SakuTheme.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = spacing.touchTarget)
            .padding(horizontal = spacing.sm, vertical = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.import_back),
                    tint = SakuTheme.colors.ink,
                )
            }
        }
        Text(
            text = title,
            style = SakuTheme.typography.title,
            color = SakuTheme.colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = spacing.sm),
        )
    }
}

@Composable
private fun ColumnScope.PreviewBody(
    preview: ImportPreview,
    onChooseAccount: (String, AccountChoice) -> Unit,
    onChooseDestination: (String?, AccountChoice) -> Unit,
    onSetIncluded: (Int, Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDiscard: () -> Unit,
) {
    val spacing = SakuTheme.spacing

    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.screen, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        FileFacts(preview)
        Tally(preview)
        AccountMappingSection(preview, onChooseAccount)
        if (preview.transferDestinations.isNotEmpty()) {
            TransferSection(preview, onChooseDestination)
        }
        CategorySection(preview)
        if (preview.discrepancies.isNotEmpty()) DiscrepancySection(preview, onSetIncluded)
        if (preview.heldBack.isNotEmpty()) HeldBackSection(preview)
        if (preview.problems.isNotEmpty()) ProblemSection(preview)
        preview.failure?.let { FailureSection(it) }
    }

    ActionRow(preview, onConfirm = onConfirm, onDiscard = onDiscard)
}

@Composable
private fun FileFacts(preview: ImportPreview) {
    Card {
        FactRow(stringResource(R.string.import_preview_file), preview.fileName)
        FactRow(
            stringResource(R.string.import_preview_rows),
            preview.tally.fileRowCount.toString(),
        )
        preview.dateSpan?.let {
            FactRow(
                stringResource(R.string.import_preview_span),
                stringResource(
                    R.string.import_preview_span_value,
                    it.first.readable(),
                    it.last.readable(),
                ),
            )
        }
    }
}

/**
 * The three counts the whole screen exists to justify. Deleted is a real row rather
 * than an omission: the user needs to see that it is zero, and it is zero structurally
 * because no import path removes or overwrites anything.
 */
@Composable
private fun Tally(preview: ImportPreview) {
    val tally = preview.tally
    Column(verticalArrangement = Arrangement.spacedBy(SakuTheme.spacing.sm)) {
        SectionTitle(stringResource(R.string.import_tally_title))
        Card {
            FactRow(
                label = stringResource(R.string.import_tally_new),
                value = if (preview.recounting) {
                    stringResource(R.string.import_preview_counting)
                } else {
                    tally.insertable.toString()
                },
                emphasise = true,
            )
            FactRow(stringResource(R.string.import_tally_already), tally.alreadyPresent.toString())
            FactRow(stringResource(R.string.import_tally_deleted), tally.deleted.toString())
        }
        Caption(stringResource(R.string.import_tally_deleted_note))
        if (tally.collapsedInFile > 0) {
            Caption(stringResource(R.string.import_tally_collapsed, tally.collapsedInFile))
        }
        if (tally.excluded > 0) {
            Caption(stringResource(R.string.import_tally_excluded, tally.excluded))
        }
    }
}

@Composable
private fun AccountMappingSection(
    preview: ImportPreview,
    onChoose: (String, AccountChoice) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(SakuTheme.spacing.sm)) {
        SectionTitle(stringResource(R.string.import_section_accounts))
        Caption(stringResource(R.string.import_accounts_explainer))
        Card {
            preview.accounts.forEachIndexed { index, mapping ->
                if (index > 0) HorizontalDivider(color = SakuTheme.colors.line)
                MappingRow(
                    title = mapping.sourceName,
                    subtitle = stringResource(R.string.import_account_rows, mapping.rowCount),
                    value = mapping.choice?.displayName,
                    placeholder = stringResource(R.string.import_account_unmapped),
                    hint = if (mapping.prefilled) {
                        stringResource(R.string.import_account_prefilled)
                    } else {
                        null
                    },
                    expanded = open == mapping.sourceName,
                    onToggle = { open = if (open == mapping.sourceName) null else mapping.sourceName },
                ) {
                    AccountChooser(
                        preview = preview,
                        newAccountName = mapping.sourceName,
                        onChoose = {
                            onChoose(mapping.sourceName, it)
                            open = null
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TransferSection(
    preview: ImportPreview,
    onChoose: (String?, AccountChoice) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    val spacing = SakuTheme.spacing

    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        SectionTitle(stringResource(R.string.import_section_transfers))
        Caption(stringResource(R.string.import_transfers_explainer))
        Card {
            preview.transferDestinations.forEachIndexed { index, group ->
                if (index > 0) HorizontalDivider(color = SakuTheme.colors.line)
                val key = group.categoryName ?: UNCATEGORISED_KEY
                MappingRow(
                    title = group.title(),
                    subtitle = group.label(),
                    value = group.choice?.displayName,
                    placeholder = stringResource(R.string.import_transfer_destination),
                    hint = null,
                    expanded = open == key,
                    onToggle = { open = if (open == key) null else key },
                ) {
                    AccountChooser(
                        preview = preview,
                        newAccountName = group.categoryName,
                        onChoose = {
                            onChoose(group.categoryName, it)
                            open = null
                        },
                    )
                }
            }
        }
    }
}

/**
 * The picker, plus a create action when the name is not already an account. Offering
 * "create" for a name that exists would make a second account with the same name and
 * split the history between them.
 */
@Composable
private fun AccountChooser(
    preview: ImportPreview,
    newAccountName: String?,
    onChoose: (AccountChoice) -> Unit,
) {
    val alreadyExists = newAccountName == null || preview.availableAccounts.any {
        it.name.equals(newAccountName, ignoreCase = true)
    }

    AccountPicker(
        accounts = preview.availableAccounts,
        onSelect = { onChoose(AccountChoice.Existing(it.id, it.name)) },
        onCreateNew = if (alreadyExists) {
            null
        } else {
            { onChoose(AccountChoice.Create(newAccountName, guessAccountType(newAccountName))) }
        },
        modifier = Modifier.heightIn(max = 280.dp),
        createLabel = newAccountName?.let { stringResource(R.string.import_account_create, it) }
            .orEmpty(),
    )
}

@Composable
private fun CategorySection(preview: ImportPreview) {
    Column(verticalArrangement = Arrangement.spacedBy(SakuTheme.spacing.sm)) {
        SectionTitle(stringResource(R.string.import_section_categories))
        Caption(
            stringResource(
                R.string.import_categories_summary,
                preview.categories.size - preview.newCategoryCount,
                preview.newCategoryCount,
            ),
        )
        Card {
            preview.categories.forEachIndexed { index, category ->
                if (index > 0) HorizontalDivider(color = SakuTheme.colors.line)
                FactRow(
                    label = category.name,
                    value = if (category.willBeCreated) {
                        stringResource(R.string.import_category_new)
                    } else {
                        stringResource(R.string.import_category_matched)
                    },
                )
            }
        }
    }
}

@Composable
private fun DiscrepancySection(preview: ImportPreview, onSetIncluded: (Int, Boolean) -> Unit) {
    val spacing = SakuTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        SectionTitle(stringResource(R.string.import_section_discrepancies))
        Caption(stringResource(R.string.import_discrepancies_explainer))
        Card {
            preview.discrepancies.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider(color = SakuTheme.colors.line)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.md, vertical = spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    Text(
                        text = stringResource(
                            R.string.import_discrepancy_row,
                            row.sourceRow,
                            formatIdr(row.idr, withPrefix = false),
                            row.declared,
                        ),
                        style = SakuTheme.typography.body,
                        color = SakuTheme.colors.ink,
                        modifier = Modifier.weight(1f),
                    )
                    Checkbox(
                        checked = row.included,
                        onCheckedChange = { onSetIncluded(row.sourceRow, it) },
                        colors = CheckboxDefaults.colors(
                            checkedColor = SakuTheme.colors.accent,
                            uncheckedColor = SakuTheme.colors.ink3,
                            checkmarkColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun HeldBackSection(preview: ImportPreview) {
    Column(verticalArrangement = Arrangement.spacedBy(SakuTheme.spacing.sm)) {
        SectionTitle(stringResource(R.string.import_section_held_back))
        Card {
            // Capped, because a badly formed file can reject every row and a list of
            // 1,200 identical complaints tells the user nothing the first twenty did not.
            preview.heldBack.take(HELD_BACK_SHOWN).forEach { Caption(it.line()) }
            if (preview.heldBack.size > HELD_BACK_SHOWN) {
                Caption("+${preview.heldBack.size - HELD_BACK_SHOWN}")
            }
        }
    }
}

@Composable
private fun ProblemSection(preview: ImportPreview) {
    Column(verticalArrangement = Arrangement.spacedBy(SakuTheme.spacing.sm)) {
        preview.problems.forEach { problem ->
            Text(
                text = problem.message(),
                style = SakuTheme.typography.body,
                color = SakuTheme.colors.warning,
            )
        }
    }
}

/**
 * The repository validates every row inside its own transaction and throws on the first
 * bad one, so this means nothing was written. Showing the message beats crashing, and
 * beats a half-imported file by a wider margin still.
 */
@Composable
private fun FailureSection(message: String) {
    Column(verticalArrangement = Arrangement.spacedBy(SakuTheme.spacing.sm)) {
        SectionTitle(stringResource(R.string.import_failed_title))
        Text(
            text = message,
            style = SakuTheme.typography.body,
            color = SakuTheme.colors.expense,
        )
    }
}

@Composable
private fun ActionRow(preview: ImportPreview, onConfirm: () -> Unit, onDiscard: () -> Unit) {
    val spacing = SakuTheme.spacing
    HorizontalDivider(color = SakuTheme.colors.line)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SakuTheme.colors.surface)
            .padding(horizontal = spacing.screen, vertical = spacing.md),
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onDiscard) {
            Text(
                text = stringResource(R.string.import_discard),
                style = SakuTheme.typography.bodyStrong,
                color = SakuTheme.colors.ink2,
            )
        }
        Button(
            onClick = onConfirm,
            enabled = preview.canImport,
            colors = ButtonDefaults.buttonColors(
                containerColor = SakuTheme.colors.accent,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = SakuTheme.colors.surfaceAlt,
                disabledContentColor = SakuTheme.colors.inkDisabled,
            ),
            modifier = Modifier.weight(1f).testTag(IMPORT_ACTION_TAG),
        ) {
            if (preview.committing) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp),
                )
            } else {
                Text(
                    text = if (preview.canImport) {
                        stringResource(R.string.import_action_count, preview.tally.insertable)
                    } else {
                        stringResource(R.string.import_action)
                    },
                    style = SakuTheme.typography.bodyStrong,
                )
            }
        }
    }
}

@Composable
private fun MappingRow(
    title: String,
    subtitle: String,
    value: String?,
    placeholder: String,
    hint: String?,
    expanded: Boolean,
    onToggle: () -> Unit,
    picker: @Composable () -> Unit,
) {
    val colors = SakuTheme.colors
    val spacing = SakuTheme.spacing

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .heightIn(min = spacing.touchTarget)
                .padding(horizontal = spacing.md, vertical = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = SakuTheme.typography.bodyStrong, color = colors.ink)
                Text(text = subtitle, style = SakuTheme.typography.caption, color = colors.ink2)
                hint?.let {
                    Text(text = it, style = SakuTheme.typography.caption, color = colors.warning)
                }
            }
            Text(
                text = value ?: placeholder,
                style = SakuTheme.typography.bodyStrong,
                color = if (value == null) colors.warning else colors.accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (expanded) {
            HorizontalDivider(color = colors.line)
            picker()
        }
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SakuTheme.colors.surface, RoundedCornerShape(SakuTheme.spacing.corner))
            .padding(vertical = SakuTheme.spacing.xs),
        content = content,
    )
}

@Composable
private fun FactRow(label: String, value: String, emphasise: Boolean = false) {
    val spacing = SakuTheme.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.md, vertical = spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = SakuTheme.typography.body,
            color = SakuTheme.colors.ink2,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = if (emphasise) SakuTheme.typography.amountSmall else SakuTheme.typography.bodyStrong,
            color = SakuTheme.colors.ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text = text, style = SakuTheme.typography.heading, color = SakuTheme.colors.ink)
}

@Composable
private fun Body(text: String) {
    Text(text = text, style = SakuTheme.typography.body, color = SakuTheme.colors.ink2)
}

@Composable
private fun Caption(text: String) {
    Text(
        text = text,
        style = SakuTheme.typography.caption,
        color = SakuTheme.colors.ink2,
        modifier = Modifier.padding(horizontal = SakuTheme.spacing.md),
    )
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(
            SakuTheme.spacing.md,
            Alignment.CenterVertically,
        ),
    ) {
        content()
    }
}

/**
 * For instrumentation, which should not depend on a label that carries a row count.
 * Maestro matches on the visible text instead — Compose test tags are not exposed as
 * resource ids here, and turning that on is a shell-wide change no track owns.
 */
const val IMPORT_ACTION_TAG = "import_confirm"

/** A leading NUL, written as an escape so the file stays plain text. No category
 *  name can contain one, so this key cannot collide with a real one. */
private const val UNCATEGORISED_KEY = "\u0000uncategorised"
private const val HELD_BACK_SHOWN = 20
