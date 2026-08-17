package com.dvnnfrr.saku.ui.importexport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dvnnfrr.saku.data.repository.AccountRepository
import com.dvnnfrr.saku.data.repository.CategoryRepository
import com.dvnnfrr.saku.data.repository.TransactionRepository
import com.dvnnfrr.saku.excel.ExportSheet
import com.dvnnfrr.saku.excel.XlsxWriter
import com.dvnnfrr.saku.sakuViewModelFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone

sealed interface ExportUiState {
    data object Idle : ExportUiState

    data class Writing(val fileName: String) : ExportUiState

    data class Written(val fileName: String, val transactionCount: Int) : ExportUiState

    data class Failed(val fileName: String, val detail: String?) : ExportUiState
}

/**
 * Writes the whole database to a sheet the importer can read back.
 *
 * Names, not ids, and both sides of every transfer — see [ExportSheet]. There is no
 * upload and no share step: the file is written to the one document the system picker
 * handed back, and the app holds no permission that would let it go anywhere else.
 */
class ExportViewModel(
    private val transactions: TransactionRepository,
    private val accounts: AccountRepository,
    private val categories: CategoryRepository,
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _state = MutableStateFlow<ExportUiState>(ExportUiState.Idle)
    val state: StateFlow<ExportUiState> = _state.asStateFlow()

    fun export(sink: DocumentSink) {
        _state.value = ExportUiState.Writing(sink.name)

        viewModelScope.launch {
            val rows = transactions.all()
            val accountNames = accounts.all().associate { it.id to it.name }
            val categoryNames = categories.allCategories().associate { it.id to it.name }
            val subcategoryNames = categories.allSubcategories().associate { it.id to it.name }

            val table = ExportSheet.build(rows, accountNames, categoryNames, subcategoryNames, zone)

            val result = withContext(io) {
                runCatching { sink.openOutput().use { XlsxWriter.write(table, it) } }
            }

            _state.value = result.fold(
                onSuccess = { ExportUiState.Written(sink.name, rows.size) },
                onFailure = { ExportUiState.Failed(sink.name, it.message) },
            )
        }
    }

    companion object {
        val Factory = sakuViewModelFactory { ExportViewModel(transactions, accounts, categories) }
    }
}
