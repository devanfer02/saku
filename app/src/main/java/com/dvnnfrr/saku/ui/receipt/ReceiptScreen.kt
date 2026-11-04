package com.dvnnfrr.saku.ui.receipt

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dvnnfrr.saku.R
import com.dvnnfrr.saku.domain.Account
import com.dvnnfrr.saku.domain.AccountType
import com.dvnnfrr.saku.domain.Category
import com.dvnnfrr.saku.domain.CategoryKind
import com.dvnnfrr.saku.domain.ColorSlot
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.receipt.ReceiptItem
import com.dvnnfrr.saku.receipt.ReceiptReading
import com.dvnnfrr.saku.receipt.ReceiptSource
import com.dvnnfrr.saku.ui.entry.NameDialog
import com.dvnnfrr.saku.ui.entry.TagPicker
import com.dvnnfrr.saku.ui.entry.TagsRow
import com.dvnnfrr.saku.ui.entry.tagPicker
import com.dvnnfrr.saku.ui.shared.AccountPickerGrid
import com.dvnnfrr.saku.ui.shared.CategoryPickerGrid
import com.dvnnfrr.saku.ui.shared.ScreenHeader
import com.dvnnfrr.saku.ui.shared.SegmentedTabs
import androidx.compose.ui.tooling.preview.Preview
import com.dvnnfrr.saku.ui.theme.SakuPreview
import com.dvnnfrr.saku.ui.theme.SakuPreviewSurface
import com.dvnnfrr.saku.ui.theme.SakuTheme
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

/** Which bottom sheet is open. A string so it survives rotation in `rememberSaveable`. */
private object Sheet {
    const val NONE = ""
    const val ACCOUNT = "account"
    const val TAGS = "tags"
    const val ONE_ROW = "one-row"
    const val GAP = "gap"
    const val ITEM = "item:"
}

/**
 * The Receipt screen, reached from Scan on Trans with the first photo. [onSaved] gets the
 * day the rows were saved on, for Trans to open there.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptScreen(
    onBack: () -> Unit,
    onSaved: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReceiptViewModel = viewModel(factory = ReceiptViewModel.Factory),
) {
    val state by viewModel.state.collectAsState()
    val asking by viewModel.askingCategory.collectAsState()
    val savedDay by viewModel.savedDay.collectAsState()
    val allTags by viewModel.allTags.collectAsState()
    var sheet by rememberSaveable { mutableStateOf(Sheet.NONE) }
    var creatingFor by rememberSaveable { mutableStateOf<String?>(null) }
    var creatingAccount by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(savedDay) { savedDay?.let(onSaved) }

    val launchers = rememberReceiptPhotoLaunchers(viewModel::cameraUri, viewModel::cameraCancelled, viewModel::addPhoto)

    ReceiptScreen(
        state = state,
        onBack = onBack,
        onCamera = launchers.camera,
        onGallery = launchers.gallery,
        onRecordAs = viewModel::setRecordAs,
        onPickTotal = viewModel::pickTotal,
        onOpenItem = { sheet = Sheet.ITEM + it },
        onOpenOneRowCategory = { sheet = Sheet.ONE_ROW },
        onGapRow = viewModel::setGapRow,
        onOpenGapCategory = { sheet = Sheet.GAP },
        onAddLine = viewModel::addLineToNote,
        onOpenAccount = { sheet = Sheet.ACCOUNT },
        onOpenTags = { sheet = Sheet.TAGS },
        onSave = viewModel::save,
        modifier = modifier,
    )

    val close = { sheet = Sheet.NONE }
    when {
        sheet == Sheet.ACCOUNT -> PickerSheet(title = stringResource(R.string.receipt_pick_account), onDismiss = close) {
            AccountPickerGrid(
                accounts = state.accounts,
                selectedId = state.account?.id,
                onSelect = { viewModel.setAccount(it.id); close() },
                onCreateNew = { creatingAccount = true },
                createLabel = stringResource(R.string.accounts_new_title),
            )
        }

        sheet == Sheet.TAGS -> PickerSheet(title = stringResource(R.string.receipt_tags), onDismiss = close) {
            var query by rememberSaveable { mutableStateOf("") }
            TagsRow(
                tags = state.tags,
                onRemove = viewModel::removeTag,
                onAdd = null,
                modifier = Modifier.padding(horizontal = SakuTheme.spacing.screen),
            )
            TagPicker(
                query = query,
                picker = tagPicker(allTags, state.tags, query),
                hasAnyTag = allTags.isNotEmpty(),
                onQuery = { query = it },
                onPick = { viewModel.pickTag(it); query = "" },
                onAddTyped = { viewModel.addTypedTag(query); query = "" },
                onDone = { viewModel.addTypedTag(query); close() },
            )
        }

        sheet.isNotEmpty() -> {
            val itemKey = sheet.removePrefix(Sheet.ITEM).takeIf { sheet.startsWith(Sheet.ITEM) }
            val item = state.items.firstOrNull { it.key == itemKey }
            val (title, selected) = when (sheet) {
                Sheet.ONE_ROW -> stringResource(R.string.receipt_category) to state.oneRowCategoryId
                Sheet.GAP -> stringResource(R.string.receipt_gap_category) to state.gapCategoryId
                else -> stringResource(R.string.receipt_pick_category, item?.name.orEmpty()) to item?.categoryId
            }
            val apply: (Long) -> Unit = { id ->
                when {
                    sheet == Sheet.ONE_ROW -> viewModel.setOneRowCategory(id)
                    sheet == Sheet.GAP -> viewModel.setGapCategory(id)
                    itemKey != null -> viewModel.setItemCategory(itemKey, id)
                }
            }
            PickerSheet(title = title, onDismiss = close) {
                if (item != null) ItemNameField(item = item, onName = { viewModel.setItemName(item.key, it) })
                CategoryPickerGrid(
                    categories = state.categories,
                    selectedId = selected,
                    onSelect = { apply(it.id); close() },
                    onCreateNew = { creatingFor = sheet },
                    createLabel = stringResource(R.string.entry_new_category),
                )
            }
        }
    }

    if (asking) {
        PickerSheet(
            title = pluralStringResource(R.plurals.receipt_missing_title, state.unsetCount, state.unsetCount),
            onDismiss = viewModel::dismissCategoryPrompt,
        ) {
            Text(
                text = stringResource(R.string.receipt_missing_body),
                style = SakuTheme.typography.body,
                color = SakuTheme.colors.ink2,
                modifier = Modifier.padding(horizontal = SakuTheme.spacing.screen),
            )
            CategoryPickerGrid(
                categories = state.categories,
                onSelect = { viewModel.useForAllUnset(it.id) },
                onCreateNew = { creatingFor = UNSET },
                createLabel = stringResource(R.string.entry_new_category),
            )
        }
    }

    if (creatingAccount) {
        NameDialog(
            titleRes = R.string.accounts_new_title,
            onConfirm = { name ->
                creatingAccount = false
                close()
                viewModel.createAccount(name)
            },
            onDismiss = { creatingAccount = false },
        )
    }

    creatingFor?.let { target ->
        NameDialog(
            titleRes = R.string.entry_new_category,
            onConfirm = { name ->
                creatingFor = null
                close()
                viewModel.createCategory(name) { id ->
                    when {
                        target == UNSET -> viewModel.useForAllUnset(id)
                        target == Sheet.ONE_ROW -> viewModel.setOneRowCategory(id)
                        target == Sheet.GAP -> viewModel.setGapCategory(id)
                        target.startsWith(Sheet.ITEM) -> viewModel.setItemCategory(target.removePrefix(Sheet.ITEM), id)
                    }
                }
            },
            onDismiss = { creatingFor = null },
        )
    }
}

private const val UNSET = "unset"

/**
 * The item's name, editable: Saku's readable name to start with, the printed one shown
 * under it. A changed word is learned at Save for the next receipt.
 */
@Composable
private fun ItemNameField(item: ReceiptItemUi, onName: (String) -> Unit) {
    var text by rememberSaveable(item.key) { mutableStateOf(item.name) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it; onName(it) },
        singleLine = true,
        label = { Text(stringResource(R.string.receipt_item_name)) },
        supportingText = { Text(stringResource(R.string.receipt_item_printed, item.printed)) },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth().padding(horizontal = SakuTheme.spacing.screen),
    )
}

/** A bottom sheet with a heading, so every choice opens in the lower half. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerSheet(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SakuTheme.colors.surface,
    ) {
        Column(
            modifier = Modifier.padding(bottom = SakuTheme.spacing.lg).imePadding(),
            verticalArrangement = Arrangement.spacedBy(SakuTheme.spacing.sm),
        ) {
            Text(
                text = title,
                style = SakuTheme.typography.heading,
                color = SakuTheme.colors.ink,
                modifier = Modifier
                    .padding(horizontal = SakuTheme.spacing.screen)
                    .semantics { heading() },
            )
            content()
        }
    }
}

/**
 * The stateless Receipt screen: the reading on top, scrolling, and the actions in a bar at
 * the bottom where the thumb is (Record as, Account, Tags, Add next part, Save).
 */
@Composable
fun ReceiptScreen(
    state: ReceiptUiState,
    onBack: () -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onRecordAs: (RecordAs) -> Unit,
    onPickTotal: (Money) -> Unit,
    onOpenItem: (String) -> Unit,
    onOpenOneRowCategory: () -> Unit,
    onGapRow: (Boolean) -> Unit,
    onOpenGapCategory: () -> Unit,
    onAddLine: (String) -> Unit,
    onOpenAccount: () -> Unit,
    onOpenTags: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().background(SakuTheme.colors.background)) {
        ScreenHeader(
            title = stringResource(R.string.receipt_title),
            onBack = onBack,
            backLabel = stringResource(R.string.screen_back),
        )
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.photos == 0 && state.reading -> CentredMessage(stringResource(R.string.receipt_reading), progress = true)
                state.photos == 0 -> CentredMessage(stringResource(R.string.receipt_nothing_read), progress = false)
                else -> ReceiptList(
                    state = state,
                    onPickTotal = onPickTotal,
                    onOpenItem = onOpenItem,
                    onOpenOneRowCategory = onOpenOneRowCategory,
                    onGapRow = onGapRow,
                    onOpenGapCategory = onOpenGapCategory,
                    onAddLine = onAddLine,
                )
            }
        }
        ReceiptBottomBar(
            state = state,
            onRecordAs = onRecordAs,
            onOpenAccount = onOpenAccount,
            onOpenTags = onOpenTags,
            onCamera = onCamera,
            onGallery = onGallery,
            onSave = onSave,
        )
    }
}

@Composable
private fun CentredMessage(text: String, progress: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize().padding(SakuTheme.spacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (progress) {
            CircularProgressIndicator(color = SakuTheme.colors.accent)
            Spacer(Modifier.size(SakuTheme.spacing.lg))
        }
        Text(
            text = text,
            style = SakuTheme.typography.body,
            color = SakuTheme.colors.ink2,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@Composable
private fun ReceiptList(
    state: ReceiptUiState,
    onPickTotal: (Money) -> Unit,
    onOpenItem: (String) -> Unit,
    onOpenOneRowCategory: () -> Unit,
    onGapRow: (Boolean) -> Unit,
    onOpenGapCategory: () -> Unit,
    onAddLine: (String) -> Unit,
) {
    val spacing = SakuTheme.spacing
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = "summary") { ReceiptSummary(state = state, onPickTotal = onPickTotal) }
        if (state.reading) {
            item(key = "reading") { ReadingRow(photo = state.photos + 1) }
        }
        if (state.unreadable) {
            item(key = "unreadable") { Notice(stringResource(R.string.receipt_unreadable)) }
        }
        items(state.gapsAfterPhoto, key = { "gap-$it" }) { after ->
            Notice(stringResource(R.string.receipt_gap_photos, after, after + 1))
        }
        when (state.recordAs) {
            RecordAs.PER_ITEM -> {
                item(key = "items-title") {
                    SectionTitle(pluralStringResource(R.plurals.receipt_items_title, state.items.size, state.items.size))
                }
                items(state.items, key = { "item-${it.key}" }) { item ->
                    ItemRow(item = item, onClick = { onOpenItem(item.key) })
                    HorizontalDivider(color = SakuTheme.colors.line, modifier = Modifier.padding(horizontal = spacing.screen))
                }
                if (state.gapRowOffered) {
                    item(key = "gap-row") {
                        GapRowControls(state = state, onGapRow = onGapRow, onOpenGapCategory = onOpenGapCategory)
                    }
                }
            }

            RecordAs.ONE_ROW -> item(key = "one-row") {
                OneRowFields(state = state, onOpenCategory = onOpenOneRowCategory, onAddLine = onAddLine)
            }
        }
        item(key = "end") { Spacer(Modifier.size(spacing.lg)) }
    }
}

@Composable
private fun ReceiptBottomBar(
    state: ReceiptUiState,
    onRecordAs: (RecordAs) -> Unit,
    onOpenAccount: () -> Unit,
    onOpenTags: () -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onSave: () -> Unit,
) {
    val spacing = SakuTheme.spacing
    val colors = SakuTheme.colors
    var menuOpen by rememberSaveable { mutableStateOf(false) }

    val recordAs: @Composable (Modifier) -> Unit = { modifier ->
        if (state.canRecordPerItem) {
            SegmentedTabs(
                options = listOf(
                    stringResource(R.string.receipt_record_one),
                    stringResource(R.string.receipt_record_per_item),
                ),
                selectedIndex = state.recordAs.ordinal,
                onSelect = { onRecordAs(RecordAs.entries[it]) },
                modifier = modifier,
            )
        } else if (state.photos > 0) {
            Text(
                text = stringResource(R.string.receipt_one_row_only),
                style = SakuTheme.typography.caption,
                color = colors.ink2,
                modifier = modifier,
            )
        }
    }
    val account = state.account?.name.orEmpty()
    val accountField: @Composable (Modifier) -> Unit = { modifier ->
        BarField(
            label = stringResource(R.string.receipt_account),
            value = account,
            spoken = stringResource(R.string.receipt_account_spoken, account),
            onClick = onOpenAccount,
            modifier = modifier,
        )
    }
    val tags = state.tags.joinToString(", ") { it.name }.ifEmpty { stringResource(R.string.receipt_no_tag) }
    val tagsField: @Composable (Modifier) -> Unit = { modifier ->
        BarField(
            label = stringResource(R.string.receipt_tags),
            value = tags,
            spoken = stringResource(R.string.receipt_tags_spoken, tags),
            onClick = onOpenTags,
            modifier = modifier,
        )
    }
    val addPart: @Composable (Modifier) -> Unit = { modifier ->
        Box(modifier = modifier) {
            OutlinedButton(
                onClick = { menuOpen = true },
                enabled = !state.reading,
                shape = RoundedCornerShape(spacing.corner),
                modifier = Modifier.fillMaxWidth().heightIn(min = spacing.touchTarget),
            ) {
                Icon(Icons.Outlined.AddAPhoto, contentDescription = null, tint = colors.accent)
                Text(
                    text = stringResource(R.string.receipt_add_part),
                    style = SakuTheme.typography.bodyStrong,
                    color = colors.accent,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(start = spacing.sm),
                )
            }
            ReceiptPhotoMenu(
                expanded = menuOpen,
                onDismiss = { menuOpen = false },
                onCamera = onCamera,
                onGallery = onGallery,
            )
        }
    }
    val save: @Composable (Modifier) -> Unit = { modifier ->
        Button(
            onClick = onSave,
            enabled = state.canSave,
            shape = RoundedCornerShape(spacing.corner),
            // onPrimary as on the keypad's Save: white on the dark accent fails contrast.
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.accent,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            modifier = modifier.heightIn(min = spacing.touchTarget),
        ) {
            Text(
                text = pluralStringResource(R.plurals.receipt_save, state.rowCount, state.rowCount),
                style = SakuTheme.typography.bodyStrong,
                textAlign = TextAlign.Center,
            )
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth().background(colors.surface)) {
        // Landscape or a tablet: one row, so the list keeps the height (found on the
        // emulator: three stacked rows left the list about 70 dp in landscape).
        val wide = maxWidth >= WIDE_BAR
        Column {
            HorizontalDivider(color = colors.line)
            if (wide) {
                Row(
                    modifier = Modifier.padding(horizontal = spacing.screen, vertical = spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    recordAs(Modifier.weight(1.4f))
                    accountField(Modifier.weight(1f))
                    tagsField(Modifier.weight(1f))
                    addPart(Modifier.weight(1.1f))
                    save(Modifier.weight(1f))
                }
            } else {
                Column(
                    modifier = Modifier.padding(horizontal = spacing.screen, vertical = spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    recordAs(Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        accountField(Modifier.weight(1f))
                        tagsField(Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                        addPart(Modifier.weight(1f))
                        save(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

private val WIDE_BAR = 600.dp

@SakuPreview
@Composable
private fun ReceiptScreenPreview() {
    val makan = Category(1, "Makan & Minum", CategoryKind.EXPENSE, ColorSlot(0), 0, false)
    val reading = ReceiptReading(
        total = Money(206_100),
        otherAmounts = listOf(Money(226_100)),
        date = LocalDate(2026, 10, 30),
        store = "Indomaret",
        unplaced = emptyList(),
        items = listOf(
            ReceiptItem("INDOMIE GRG SPCL", 5, Money(15_500)),
            ReceiptItem("MINYAK GORENG 2L", 1, Money(38_900)),
            ReceiptItem("BERAS PREMIUM 5KG", 1, Money(72_500)),
            ReceiptItem("SUSU UHT 1L", 2, Money(36_400), Money(4_000)),
            ReceiptItem("SABUN CUCI 800ML", 1, Money(21_900)),
            ReceiptItem("DETERJEN 1.8KG", 1, Money(44_900)),
        ),
        receiptDiscounts = listOf(Money(20_000)),
    )
    val card = Account(
        2, "Card", AccountType.entries.first(), ColorSlot(1), 0, false,
        Money.ZERO, Instant.fromEpochSeconds(0), null, Money.ZERO,
    )
    val state = assembleReceipt(
        reading = reading,
        choices = ReceiptChoices(),
        dictionary = mapOf("MINYAK GORENG 2L" to 1L, "SUSU UHT 1L" to 1L),
        categories = listOf(makan),
        accounts = listOf(card),
        allTags = emptyList(),
        defaults = ReceiptDefaults(),
        photos = 3,
        readingPhoto = false,
    )
    SakuPreviewSurface(padded = false) {
        ReceiptScreen(
            state = state,
            onBack = {}, onCamera = {}, onGallery = {}, onRecordAs = {}, onPickTotal = {},
            onOpenItem = {}, onOpenOneRowCategory = {}, onGapRow = {}, onOpenGapCategory = {},
            onAddLine = {}, onOpenAccount = {}, onOpenTags = {}, onSave = {},
        )
    }
}

/** A GoFood order at 200% font: the Read from line wraps in the list and the Save bar stays put. */
@Preview(name = "GoFood, 200% font", showBackground = true, fontScale = 2f, heightDp = 800)
@Composable
private fun ReceiptOrderLargeFontPreview() {
    val makan = Category(1, "Makan & Minum", CategoryKind.EXPENSE, ColorSlot(0), 0, false)
    val reading = ReceiptReading(
        total = Money(63_100),
        otherAmounts = emptyList(),
        date = LocalDate(2026, 11, 3),
        store = null,
        unplaced = listOf("Flavour : Smoky Barbeque"),
        items = listOf(ReceiptItem("Big Crunch Chicken", 1, Money(49_000))),
        receiptDiscounts = listOf(Money(14_700)),
        additions = listOf(Money(22_800), Money(6_000)),
        source = ReceiptSource.GOFOOD,
    )
    val state = assembleReceipt(
        reading = reading,
        choices = ReceiptChoices(),
        dictionary = emptyMap(),
        categories = listOf(makan),
        accounts = emptyList(),
        allTags = emptyList(),
        defaults = ReceiptDefaults(),
        photos = 1,
        readingPhoto = false,
    )
    SakuPreviewSurface(padded = false) {
        ReceiptScreen(
            state = state,
            onBack = {}, onCamera = {}, onGallery = {}, onRecordAs = {}, onPickTotal = {},
            onOpenItem = {}, onOpenOneRowCategory = {}, onGapRow = {}, onOpenGapCategory = {},
            onAddLine = {}, onOpenAccount = {}, onOpenTags = {}, onSave = {},
        )
    }
}
