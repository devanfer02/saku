package com.dvnnfrr.saku.ui.importexport

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dvnnfrr.saku.container
import com.dvnnfrr.saku.data.repository.ImportRepository
import com.dvnnfrr.saku.excel.BackupSheets
import com.dvnnfrr.saku.excel.XlsxWriter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

data class BackupCounts(
    val accounts: Int,
    val categories: Int,
    val subcategories: Int,
    val transactions: Int,
)

sealed interface BackupUiState {
    data object Idle : BackupUiState

    data class Writing(val fileName: String) : BackupUiState

    data class Written(val fileName: String, val counts: BackupCounts) : BackupUiState

    data class Failed(val fileName: String, val detail: String?) : BackupUiState
}

/**
 * Writes the whole database to one workbook through the document the picker handed back.
 *
 * Like [ExportViewModel] there is no upload and no share step. The difference is what goes
 * in: every record sheet as well as the transactions, so the file rebuilds the app rather
 * than only its history. See [BackupSheets].
 */
class BackupViewModel(
    private val imports: ImportRepository,
    private val appVersion: String,
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
    private val clock: Clock = Clock.System,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _state = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    /** The name the create-document picker suggests, dated today in the user's zone. */
    fun suggestedFileName(): String = backupFileName(clock.now().toLocalDateTime(zone).date)

    /** [sink] is null when the user backed out of the picker: nothing is written or reported. */
    fun backUp(sink: DocumentSink?) {
        if (sink == null) return
        _state.value = BackupUiState.Writing(sink.name)

        viewModelScope.launch {
            val result = runCatching {
                val snapshot = imports.backupSnapshot()
                val sheets = BackupSheets.build(snapshot, appVersion, clock.now(), zone)
                withContext(io) { sink.openOutput().use { XlsxWriter.write(sheets, it) } }
                BackupCounts(
                    accounts = snapshot.accounts.size,
                    categories = snapshot.categories.size,
                    subcategories = snapshot.subcategories.size,
                    transactions = snapshot.transactions.size,
                )
            }

            _state.value = result.fold(
                onSuccess = { BackupUiState.Written(sink.name, it) },
                onFailure = { BackupUiState.Failed(sink.name, it.message) },
            )
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                BackupViewModel(container().imports, appVersionOf(application))
            }
        }

        /** Recorded in the `Backup` sheet so a refusal can name the version a file needs. */
        private fun appVersionOf(context: Context): String =
            runCatching {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrNull().orEmpty()
    }
}

/** `Saku backup 2026-10-30.xlsx`. A file name, not UI copy, so it is not translated. */
internal fun backupFileName(date: LocalDate): String = "Saku backup $date.xlsx"
