package com.dvnnfrr.saku.ui.importexport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dvnnfrr.saku.data.local.DatabaseFileReader
import com.dvnnfrr.saku.data.repository.AccountRepository
import com.dvnnfrr.saku.data.repository.CategoryRepository
import com.dvnnfrr.saku.data.repository.ImportRepository
import com.dvnnfrr.saku.excel.BackupContents
import com.dvnnfrr.saku.excel.DatabaseReadOutcome
import com.dvnnfrr.saku.excel.ExistingRecords
import com.dvnnfrr.saku.excel.RestorePlan
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
import java.io.File

/**
 * Reads a SQLite file in Saku's schema, previews what importing it would add, and imports
 * on confirmation through the backup restore.
 *
 * The picked document is copied into [scratchDir] because SQLite opens a path, not a
 * stream; the copy is removed once read. Nothing before [confirm] writes, and the app's own
 * database file is never involved: the rows go through `ImportRepository.restore`, which
 * inserts only and rebuilds its plan inside its own transaction.
 */
class DatabaseImportViewModel(
    private val imports: ImportRepository,
    private val accounts: AccountRepository,
    private val categories: CategoryRepository,
    private val scratchDir: File,
    private val read: (File) -> DatabaseReadOutcome = DatabaseFileReader::read,
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
    private val clock: Clock = Clock.System,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _state = MutableStateFlow<DatabaseImportUiState>(DatabaseImportUiState.Idle)
    val state: StateFlow<DatabaseImportUiState> = _state.asStateFlow()

    /** The adapted file behind the preview on screen. */
    private var contents: BackupContents? = null

    /** [source] is null when the user backed out of the picker: the screen stays as it was. */
    fun load(source: DocumentSource?) {
        if (source == null) return
        contents = null
        _state.value = DatabaseImportUiState.Reading(source.name)

        viewModelScope.launch {
            val outcome = withContext(io) { runCatching { readCopy(source) } }.getOrElse { failure ->
                _state.value = DatabaseImportUiState.Refused(
                    source.name,
                    DatabaseImportRefusal.Unreadable(failure.message),
                )
                return@launch
            }

            val backup = when (outcome) {
                is DatabaseReadOutcome.Refused -> {
                    _state.value = DatabaseImportUiState.Refused(
                        source.name,
                        DatabaseImportRefusal.Database(outcome.refusal),
                    )
                    return@launch
                }

                is DatabaseReadOutcome.Read -> outcome.contents
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
            _state.value = DatabaseImportUiState.Ready(
                databaseImportPreview(source.name, plan, hashes, present, zone),
            )
        }
    }

    fun confirm() {
        val ready = _state.value as? DatabaseImportUiState.Ready ?: return
        val backup = contents ?: return
        if (ready.committing || !ready.preview.hasAnythingNew) return

        _state.value = ready.copy(committing = true, failure = null)

        viewModelScope.launch {
            try {
                val result = imports.restore(backup, ready.preview.fileName, clock.now())
                contents = null
                _state.value = DatabaseImportUiState.Imported(ready.preview.fileName, result)
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
        _state.value = DatabaseImportUiState.Idle
    }

    /** The copy is this screen's own temporary file, removed whether or not it read. */
    private fun readCopy(source: DocumentSource): DatabaseReadOutcome {
        val copy = File.createTempFile("database-import-", ".db", scratchDir)
        try {
            source.openInput().use { input -> copy.outputStream().use { input.copyTo(it) } }
            return read(copy)
        } finally {
            copy.delete()
        }
    }

    companion object {
        val Factory = sakuViewModelFactory {
            DatabaseImportViewModel(imports, accounts, categories, scratchDir = cacheDir)
        }
    }
}
