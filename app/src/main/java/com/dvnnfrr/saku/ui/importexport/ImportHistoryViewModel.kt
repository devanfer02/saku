package com.dvnnfrr.saku.ui.importexport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dvnnfrr.saku.data.repository.ImportRepository
import com.dvnnfrr.saku.domain.ImportBatch
import com.dvnnfrr.saku.sakuViewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

data class ImportHistoryUiState(
    val batches: List<ImportBatch> = emptyList(),
    val loading: Boolean = true,
    /** The batch the confirmation dialog is asking about. */
    val confirming: ImportBatch? = null,
)

/**
 * Past imports, and the ability to undo one whole.
 *
 * Reversal is the safety net that makes a one-tap import of a year defensible:
 * a mapping the user got wrong costs a tap to undo rather than a manual sweep through
 * 1,200 rows. It removes only the transactions that batch inserted — the repository
 * deletes by batch id, so hand-entered rows and other batches are untouched.
 */
class ImportHistoryViewModel(
    private val imports: ImportRepository,
    private val clock: Clock = Clock.System,
) : ViewModel() {

    private val confirming = MutableStateFlow<ImportBatch?>(null)

    val state: StateFlow<ImportHistoryUiState> =
        combine(imports.observeBatches(), confirming) { batches, pending ->
            ImportHistoryUiState(batches = batches, loading = false, confirming = pending)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ImportHistoryUiState(),
        )

    fun askToReverse(batch: ImportBatch) {
        confirming.value = batch
    }

    fun dismissConfirmation() {
        confirming.value = null
    }

    fun confirmReversal() {
        val batch = confirming.value ?: return
        confirming.value = null
        viewModelScope.launch { imports.reverse(batch.id, clock.now()) }
    }

    companion object {
        val Factory = sakuViewModelFactory { ImportHistoryViewModel(imports) }
    }
}
