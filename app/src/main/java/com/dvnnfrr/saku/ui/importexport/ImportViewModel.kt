package com.dvnnfrr.saku.ui.importexport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dvnnfrr.saku.container
import com.dvnnfrr.saku.data.repository.AccountRepository
import com.dvnnfrr.saku.data.repository.CategoryRepository
import com.dvnnfrr.saku.data.repository.ImportRepository
import com.dvnnfrr.saku.domain.Account
import com.dvnnfrr.saku.domain.Category
import com.dvnnfrr.saku.domain.CategoryKind
import com.dvnnfrr.saku.domain.TxnType
import com.dvnnfrr.saku.excel.ImportParser
import com.dvnnfrr.saku.excel.ImportPlan
import com.dvnnfrr.saku.excel.ImportTally
import com.dvnnfrr.saku.excel.ParseOutcome
import com.dvnnfrr.saku.excel.ParsedRow
import com.dvnnfrr.saku.excel.ParsedSheet
import com.dvnnfrr.saku.excel.RowMappings
import com.dvnnfrr.saku.excel.SubcategoryKey
import com.dvnnfrr.saku.excel.XlsxReader
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

/**
 * Reads a workbook, maps its names onto records, and commits — in that order, with the
 * user's confirmation between the second step and the third.
 *
 * Nothing before [confirm] touches a repository except to read. The preview is a value
 * that is rebuilt on every choice, so "abandoning the preview leaves no trace" is a
 * property of the design rather than a promise: there is no partial write to undo.
 */
class ImportViewModel(
    private val imports: ImportRepository,
    private val accounts: AccountRepository,
    private val categories: CategoryRepository,
    entryPoint: ImportEntryPoint,
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
    private val clock: Clock = Clock.System,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _state = MutableStateFlow<ImportUiState>(ImportUiState.Idle(entryPoint))
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    private var draft: Draft? = null

    /**
     * Everything the preview is derived from. Kept apart from [ImportPreview] so a
     * mapping change re-renders rather than patching a dozen fields, and so the parsed
     * sheet is never rebuilt just because a dropdown moved.
     */
    private data class Draft(
        val entryPoint: ImportEntryPoint,
        val fileName: String,
        val sheet: ParsedSheet,
        val existingAccounts: List<Account>,
        val existingCategories: List<Category>,
        val accountChoices: Map<String, AccountChoice>,
        val prefilled: Set<String>,
        val destinationChoices: Map<String?, AccountChoice>,
        val excludedRows: Set<Int>,
        /**
         * One dedup hash per included row, in row order.
         *
         * Cached rather than derived on demand: rendering the preview would otherwise
         * SHA-256 all 1,200 rows on the main thread on every tap, and the screen has
         * three things on it a tap can change.
         */
        val hashes: List<String>,
        val existingHashes: Set<String>,
        val recounting: Boolean = false,
        val committing: Boolean = false,
        val failure: String? = null,
    )

    fun load(source: DocumentSource) {
        val entryPoint = _state.value.entryPoint
        _state.value = ImportUiState.Reading(entryPoint, source.name)

        viewModelScope.launch {
            val table = withContext(io) {
                runCatching { source.openInput().use(XlsxReader::read) }
            }.getOrElse { failure ->
                _state.value = ImportUiState.Refused(
                    entryPoint,
                    source.name,
                    ReadFailure.Unreadable(failure.message),
                )
                return@launch
            }

            val parsed = when (val outcome = withContext(io) { ImportParser.parse(table, zone) }) {
                is ParseOutcome.UnrecognisedLayout -> {
                    _state.value = ImportUiState.Refused(
                        entryPoint,
                        source.name,
                        ReadFailure.UnrecognisedLayout(outcome.mismatch),
                    )
                    return@launch
                }

                is ParseOutcome.Parsed -> outcome.sheet
            }

            if (parsed.rows.isEmpty() && parsed.rejected.isEmpty()) {
                _state.value = ImportUiState.Refused(entryPoint, source.name, ReadFailure.NoUsableRows)
                return@launch
            }

            val existingAccounts = accounts.all()
            val existingCategories = categories.allCategories()
            val prefill = prefillAccounts(parsed, existingAccounts)

            draft = Draft(
                entryPoint = entryPoint,
                fileName = source.name,
                sheet = parsed,
                existingAccounts = existingAccounts,
                existingCategories = existingCategories,
                accountChoices = prefill,
                prefilled = prefill.keys,
                // Transfer destinations are deliberately NOT prefilled, even where a
                // category name matches an account exactly. Every other guess on this
                // screen is recoverable by editing a row afterwards; this one decides
                // whether 5.050.800 lands in an investment account or vanishes, and it
                // is two taps. The user says it.
                destinationChoices = emptyMap(),
                excludedRows = emptySet(),
                hashes = emptyList(),
                existingHashes = emptySet(),
                recounting = true,
            )
            publish()
            recount()
        }
    }

    fun chooseAccount(sourceName: String, choice: AccountChoice) {
        val current = draft ?: return
        draft = current.copy(
            accountChoices = current.accountChoices + (sourceName to choice.normalised(current)),
            prefilled = current.prefilled - sourceName,
            recounting = true,
            failure = null,
        )
        publish()
        scheduleRecount()
    }

    fun chooseTransferDestination(categoryName: String?, choice: AccountChoice) {
        val current = draft ?: return
        draft = current.copy(
            destinationChoices =
                current.destinationChoices + (categoryName to choice.normalised(current)),
            recounting = true,
            failure = null,
        )
        publish()
        scheduleRecount()
    }

    /** Scenario: a row whose two amount columns disagree is included or dropped by hand. */
    fun setDiscrepancyIncluded(sourceRow: Int, included: Boolean) {
        val current = draft ?: return
        draft = current.copy(
            excludedRows = if (included) {
                current.excludedRows - sourceRow
            } else {
                current.excludedRows + sourceRow
            },
            recounting = true,
            failure = null,
        )
        publish()
        scheduleRecount()
    }

    /** Leaves the preview with nothing written, which is the whole point of having one. */
    fun discard() {
        val entryPoint = _state.value.entryPoint
        draft = null
        _state.value = ImportUiState.Idle(entryPoint)
    }

    fun confirm() {
        val current = draft ?: return
        if (!render(current).canImport) return

        draft = current.copy(committing = true, failure = null)
        publish()

        viewModelScope.launch {
            val rows = includedRows(current)
            // An account or category invented by the import opens on the day of the
            // earliest row it carries, not today. Opening it today would put every
            // imported transaction before its own account's as-of date, where the
            // balance query cannot see it.
            val openingAsOf = rows.minOfOrNull { it.occurredAt }?.startOfLocalDay() ?: clock.now()

            try {
                val accountIds = resolveAccounts(current, openingAsOf)
                val destinationIds = resolveDestinations(current, openingAsOf)
                val categoryIds = resolveCategories(rows)
                val subcategoryIds = resolveSubcategories(rows, categoryIds)

                val pending = ImportPlan.pendingRows(
                    rows = rows,
                    mappings = RowMappings(
                        accounts = accountIds,
                        transferDestinations = destinationIds,
                        categories = categoryIds,
                        subcategories = subcategoryIds,
                    ),
                )

                // Read before the commit, because afterwards every row is present and
                // the summary could no longer tell what this import added.
                val presentBefore = imports.existingHashes(pending.map { it.dedupHash })
                val batch = imports.commit(
                    fileName = current.fileName,
                    rows = pending,
                    fileRowCount = current.sheet.fileRowCount,
                    at = clock.now(),
                )

                draft = null
                _state.value = ImportUiState.Imported(
                    entryPoint = current.entryPoint,
                    summary = importSummary(
                        batch = batch,
                        rows = pending,
                        presentBefore = presentBefore,
                        accountNames = accounts.all().associate { it.id to it.name },
                        createdCategories = render(current).categories
                            .filter { it.willBeCreated }
                            .map { it.name },
                        zone = zone,
                    ),
                )
            } catch (invalid: IllegalArgumentException) {
                // commit validates every row inside its own transaction and throws on
                // the first bad one, so no transaction was inserted. Any account or
                // category resolved above may already exist — they are empty and can be
                // deleted, and guessing which ones to unpick would be worse than leaving
                // them for the retry to reuse.
                draft = current.copy(committing = false, failure = invalid.message)
                publish()
            } catch (unavailable: IllegalStateException) {
                draft = current.copy(committing = false, failure = unavailable.message)
                publish()
            }
        }
    }

    private fun scheduleRecount() {
        viewModelScope.launch { recount() }
    }

    private suspend fun recount() {
        val at = draft ?: return
        val hashes = withContext(io) {
            ImportPlan.previewHashes(
                rows = includedRows(at),
                accounts = resolvedIds(at.accountChoices),
                transferDestinations = resolvedIds(at.destinationChoices),
            )
        }
        val existing = imports.existingHashes(hashes)

        // A second file can be loaded, or another mapping made, while this is in flight.
        // Only apply the answer if it still describes the draft on screen.
        if (draft !== at) return
        draft = at.copy(hashes = hashes, existingHashes = existing, recounting = false)
        publish()
    }

    private fun publish() {
        draft?.let { _state.value = ImportUiState.Ready(render(it)) }
    }

    private fun includedRows(draft: Draft): List<ParsedRow> =
        draft.sheet.rows.filterNot { it.sourceRow in draft.excludedRows }

    /**
     * "Create Gopay" against a database that already has a Gopay is the same choice as
     * "use Gopay" — [AccountRepository.findOrCreate] matches by name and hands back the
     * existing account either way. Collapsing it here rather than at commit keeps the
     * preview's tally honest: a create-choice carries a placeholder id, so the rows
     * using it would be counted as new when in fact they are already present.
     */
    private fun AccountChoice.normalised(draft: Draft): AccountChoice {
        if (this !is AccountChoice.Create) return this
        val existing = draft.existingAccounts
            .firstOrNull { it.name.trim().equals(name.trim(), ignoreCase = true) }
        return existing?.let { AccountChoice.Existing(it.id, it.name) } ?: this
    }

    private fun <K> resolvedIds(choices: Map<K, AccountChoice>): Map<K, Long> =
        choices.mapNotNull { (key, choice) ->
            (choice as? AccountChoice.Existing)?.let { key to it.id }
        }.toMap()

    private fun render(draft: Draft): ImportPreview {
        val rows = includedRows(draft)
        val tally = ImportPlan.tally(
            sheet = draft.sheet,
            hashes = draft.hashes,
            existing = draft.existingHashes,
            excluded = draft.excludedRows.size,
        )

        val accountMappings = accountNamesIn(draft.sheet).map { (name, count) ->
            AccountMapping(
                sourceName = name,
                rowCount = count,
                choice = draft.accountChoices[name],
                prefilled = name in draft.prefilled,
            )
        }

        val transfers = draft.sheet.transferGroupsNeedingDestination.map { group ->
            TransferDestinationMapping(
                categoryName = group.categoryName,
                rowCount = group.rowCount,
                total = group.total,
                choice = draft.destinationChoices[group.categoryName],
            )
        }

        val byName = draft.existingCategories.associateBy { it.name.trim().lowercase() }
        val categoryOutcomes = rows.mapNotNull { it.categoryName }
            .groupingBy { it }
            .eachCount()
            .map { (name, count) ->
                CategoryOutcome(name, count, byName[name.trim().lowercase()]?.id)
            }
            .sortedBy { it.name }

        return ImportPreview(
            entryPoint = draft.entryPoint,
            fileName = draft.fileName,
            layout = draft.sheet.layout,
            tally = tally,
            dateSpan = draft.sheet.dateSpan(),
            accounts = accountMappings,
            transferDestinations = transfers,
            categories = categoryOutcomes,
            discrepancies = discrepancies(draft),
            heldBack = draft.sheet.rejected,
            problems = problems(draft, accountMappings, transfers, tally),
            availableAccounts = draft.existingAccounts,
            recounting = draft.recounting,
            committing = draft.committing,
            failure = draft.failure,
        )
    }

    private fun discrepancies(draft: Draft): List<DiscrepancyRow> =
        draft.sheet.rows.mapNotNull { row ->
            row.discrepancy?.let {
                DiscrepancyRow(
                    sourceRow = row.sourceRow,
                    idr = row.amount,
                    declared = it.declared,
                    included = row.sourceRow !in draft.excludedRows,
                )
            }
        }

    private fun problems(
        draft: Draft,
        accountMappings: List<AccountMapping>,
        transfers: List<TransferDestinationMapping>,
        tally: ImportTally,
    ): List<ImportProblem> = buildList {
        accountMappings.filter { it.choice == null }
            .forEach { add(ImportProblem.UnmappedAccount(it.sourceName)) }
        transfers.filter { it.choice == null }
            .forEach { add(ImportProblem.UnmappedTransferDestination(it.categoryName)) }

        addAll(sameAccountTransfers(draft, transfers))
        addAll(transfersBeforeOpening(draft))

        if (isEmpty() && tally.insertable == 0 && !draft.recounting) add(ImportProblem.NothingToImport)
    }

    /**
     * A group whose destination is also the account the transfers came from. The
     * repository refuses it, and rightly — money that leaves and re-enters the same
     * account has not moved.
     */
    private fun sameAccountTransfers(
        draft: Draft,
        transfers: List<TransferDestinationMapping>,
    ): List<ImportProblem> = transfers.mapNotNull { group ->
        val destination = group.choice ?: return@mapNotNull null
        val sources = includedRows(draft)
            .filter { it.type == TxnType.TRANSFER && it.toAccountName == null && it.categoryName == group.categoryName }
            .mapNotNull { draft.accountChoices[it.accountName] }
            .distinct()
        val clash = sources.firstOrNull { it.sameAccountAs(destination) } ?: return@mapNotNull null
        ImportProblem.TransferDestinationSameAsSource(group.categoryName, clash.displayName)
    }

    /**
     * Transfers dated before an account opened.
     *
     * The balance query ignores anything earlier than an account's as-of date, so such a
     * transfer is debited on one leg and never credited on the other: the amount is
     * destroyed rather than moved. `commit` throws on the first one and abandons the
     * whole batch, so the preview has to find them first and name the date to change.
     * A first run seeds every account as opening today, which makes this the default
     * outcome of importing a year of history rather than an edge case.
     */
    private fun transfersBeforeOpening(draft: Draft): List<ImportProblem> {
        val transfers = includedRows(draft).filter { it.type == TxnType.TRANSFER }
        if (transfers.isEmpty()) return emptyList()

        val earliestByAccount = mutableMapOf<Long, Instant>()
        fun note(id: Long?, at: Instant) {
            if (id == null) return
            earliestByAccount[id] = minOf(earliestByAccount[id] ?: at, at)
        }

        transfers.forEach { row ->
            note((draft.accountChoices[row.accountName] as? AccountChoice.Existing)?.id, row.occurredAt)
            val destination = row.toAccountName
                ?.let { draft.accountChoices[it] }
                ?: draft.destinationChoices[row.categoryName]
            note((destination as? AccountChoice.Existing)?.id, row.occurredAt)
        }

        return earliestByAccount.mapNotNull { (id, earliest) ->
            val account = draft.existingAccounts.firstOrNull { it.id == id } ?: return@mapNotNull null
            if (earliest >= account.openingAsOf) return@mapNotNull null
            ImportProblem.TransferPredatesOpening(
                accountName = account.name,
                openingDate = account.openingAsOf.toLocalDateTime(zone).date,
                earliestTransfer = earliest.toLocalDateTime(zone).date,
            )
        }.sortedBy { it.accountName }
    }

    /**
     * `Card` is 1,150 of the 1,200 reference rows and it is not a bank — the source app
     * collapsed every card, transfer and e-wallet payment onto one label. BCA is where
     * that history honestly belongs, so it arrives chosen but visibly a guess. Anything
     * whose name already matches an account maps to it; everything else waits.
     */
    private fun prefillAccounts(
        sheet: ParsedSheet,
        existing: List<Account>,
    ): Map<String, AccountChoice> {
        val byName = existing.associateBy { it.name.trim().lowercase() }
        return accountNamesIn(sheet).keys.mapNotNull { name ->
            val matched = byName[name.trim().lowercase()]
                ?: byName[DEFAULT_CARD_ACCOUNT.lowercase()].takeIf { name.equals(CARD, ignoreCase = true) }
            matched?.let { name to AccountChoice.Existing(it.id, it.name) }
        }.toMap()
    }

    /** Every name in an account position, source or destination, with how many rows use it. */
    private fun accountNamesIn(sheet: ParsedSheet): Map<String, Int> {
        val counts = linkedMapOf<String, Int>()
        sheet.rows.forEach { row ->
            counts[row.accountName] = (counts[row.accountName] ?: 0) + 1
            row.toAccountName?.let { counts[it] = (counts[it] ?: 0) + 1 }
        }
        return counts.toSortedMap()
    }

    private suspend fun resolveAccounts(draft: Draft, openingAsOf: Instant): Map<String, Long> =
        draft.accountChoices.mapValues { (_, choice) -> choice.resolve(openingAsOf) }

    private suspend fun resolveDestinations(draft: Draft, openingAsOf: Instant): Map<String?, Long> =
        draft.destinationChoices.mapValues { (_, choice) -> choice.resolve(openingAsOf) }

    private suspend fun AccountChoice.resolve(openingAsOf: Instant): Long = when (this) {
        is AccountChoice.Existing -> id
        is AccountChoice.Create -> accounts.findOrCreate(name, type, openingAsOf)
    }

    private fun AccountChoice.sameAccountAs(other: AccountChoice): Boolean = when {
        this is AccountChoice.Existing && other is AccountChoice.Existing -> id == other.id
        else -> displayName.equals(other.displayName, ignoreCase = true)
    }

    /**
     * A category's kind follows the rows that use it. A name used only by income rows is
     * an income category; everything else, transfers included, is an expense — a
     * `Transfer-Out` to an investment is money leaving the spendable side.
     */
    private suspend fun resolveCategories(rows: List<ParsedRow>): Map<String, Long> {
        val kinds = mutableMapOf<String, CategoryKind>()
        rows.forEach { row ->
            val name = row.categoryName ?: return@forEach
            val kind = if (row.type == TxnType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
            kinds[name] = if (kinds[name] == CategoryKind.EXPENSE) CategoryKind.EXPENSE else kind
        }
        return kinds.mapValues { (name, kind) -> categories.findOrCreate(name, kind) }
    }

    private suspend fun resolveSubcategories(
        rows: List<ParsedRow>,
        categoryIds: Map<String, Long>,
    ): Map<SubcategoryKey, Long> {
        val keys = rows.mapNotNull { row ->
            val category = row.categoryName ?: return@mapNotNull null
            val subcategory = row.subcategoryName ?: return@mapNotNull null
            SubcategoryKey(category, subcategory)
        }.distinct()

        return keys.mapNotNull { key ->
            val parent = categoryIds[key.categoryName] ?: return@mapNotNull null
            key to categories.findOrCreateSubcategory(parent, key.subcategoryName)
        }.toMap()
    }

    private fun ParsedSheet.dateSpan(): DateSpan? {
        val first = earliest ?: return null
        val last = latest ?: return null
        return DateSpan(first.localDate(), last.localDate())
    }

    private fun Instant.localDate(): LocalDate = toLocalDateTime(zone).date

    private fun Instant.startOfLocalDay(): Instant = localDate().atStartOfDayIn(zone)

    companion object {
        private const val CARD = "Card"

        /** Where the source app's one collapsed account label honestly belongs. */
        const val DEFAULT_CARD_ACCOUNT = "BCA"

        fun factory(entryPoint: ImportEntryPoint): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = container()
                ImportViewModel(
                    imports = container.imports,
                    accounts = container.accounts,
                    categories = container.categories,
                    entryPoint = entryPoint,
                )
            }
        }
    }
}
