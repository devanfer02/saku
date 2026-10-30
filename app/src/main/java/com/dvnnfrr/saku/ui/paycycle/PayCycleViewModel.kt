package com.dvnnfrr.saku.ui.paycycle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dvnnfrr.saku.data.PeriodStore
import com.dvnnfrr.saku.sakuViewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * The pay cycle setting. It holds no state of its own: the start day lives in
 * [PeriodStore], where Trans and Stats read it too, and the previews are assembled from it.
 */
class PayCycleViewModel(
    private val store: PeriodStore,
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
    private val clock: Clock = Clock.System,
) : ViewModel() {

    private fun today(): LocalDate = clock.now().toLocalDateTime(zone).date

    val state: StateFlow<PayCycleUiState> = store.settings
        .map { assemblePayCycle(it.startDay, today()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = assemblePayCycle(store.current().startDay, today()),
        )

    fun selectStartDay(day: Int) = store.setStartDay(day)

    fun clear() = store.setStartDay(null)

    companion object {
        val Factory = sakuViewModelFactory { PayCycleViewModel(period) }
    }
}
