package com.dvnnfrr.saku.ui.importexport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dvnnfrr.saku.data.repository.AccountRepository
import com.dvnnfrr.saku.data.repository.CategoryRepository
import com.dvnnfrr.saku.data.repository.ImportRepository
import com.dvnnfrr.saku.excel.BackupContents
import com.dvnnfrr.saku.excel.BackupReadOutcome
import com.dvnnfrr.saku.excel.BackupSheets
import com.dvnnfrr.saku.excel.ExistingRecords
import com.dvnnfrr.saku.excel.RestorePlan
import com.dvnnfrr.saku.excel.XlsxReader
import com.dvnnfrr.saku.sakuViewModelFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone

/**
 * Reads a backup, previews what restoring it would add, and restores on confirmation.
 *
 * Nothing before [confirm] writes. The preview is computed from repository reads; the
 * restore itself rebuilds the same plan inside its own transaction, so a confirm always
 * acts on what the database holds at that moment rather than on a stale preview.
 */
class RestoreViewModel(
    private val imports: ImportRepository,
    private val accounts: AccountRepository,
    private val categories: CategoryRepository,
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
    private val clock: Clock = Clock.System,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _state = MutableStateFlow<RestoreUiState>(RestoreUiState.Idle)
    val state: StateFlow<RestoreUiState> = _state.asStateFlow()

    /** The parsed file behind the preview on screen. */
    private var contents: BackupContents? = null

    /** [source] is null when the user backed out of the picker: the screen stays as it was. */
    fun load(source: DocumentSource?) {
        if (source == null) return
        contents = null
        _state.value = RestoreUiState.Reading(source.name)

        viewModelScope.launch {
            val sheets = withContext(io) {
                runCatching { source.openInput().use(XlsxReader::readAll) }
            }.getOrElse { failure ->
                _state.value = RestoreUiState.Refused(source.name, RestoreRefusal.Unreadable(failure.message))
                return@launch
            }

            val backup = when (val outcome = withContext(io) { BackupSheets.parse(sheets) }) {
                is BackupReadOutcome.Refused -> {
                    _state.value = RestoreUiState.Refused(source.name, RestoreRefusal.Backup(outcome.refusal))
                    return@launch
                }

                is BackupReadOutcome.Read -> outcome.contents
            }

            val existing = ExistingRecords(
                accounts = accounts.all(),
                categories = categories.allCategories(),
                subcategories = categories.allSubcategories(),
            )
            val plan = RestorePlan.of(backup, existing)
            val hashes = withContext(io) { plan.previewHashes() }
            val present = imports.existingHashes(hashes)

            contents = backup
            _state.value = RestoreUiState.Ready(restorePreview(source.name, plan, hashes, present, zone))
        }
    }

    fun confirm() {
        val ready = _state.value as? RestoreUiState.Ready ?: return
        val backup = contents ?: return
        if (ready.committing || !ready.preview.hasAnythingNew) return

        _state.value = ready.copy(committing = true, failure = null)

        viewModelScope.launch {
            try {
                val result = imports.restore(backup, ready.preview.fileName, clock.now())
                contents = null
                _state.value = RestoreUiState.Restored(ready.preview.fileName, result)
            } catch (invalid: IllegalArgumentException) {
                // restore runs in one transaction, so a refused row rolled back every
                // record it had created as well.
                _state.value = ready.copy(committing = false, failure = invalid.message)
            } catch (unavailable: IllegalStateException) {
                _state.value = ready.copy(committing = false, failure = unavailable.message)
            }
        }
    }

    /** Leaves the preview with nothing written. */
    fun discard() {
        contents = null
        _state.value = RestoreUiState.Idle
    }

    companion object {
        val Factory = sakuViewModelFactory { RestoreViewModel(imports, accounts, categories) }
    }
}
