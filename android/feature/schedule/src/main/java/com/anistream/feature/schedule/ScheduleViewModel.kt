package com.anistream.feature.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anistream.core.common.Result
import com.anistream.domain.model.ScheduleEntry
import com.anistream.domain.usecase.GetScheduleUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

data class ScheduleUiState(
    val isLoading: Boolean = true,
    val schedule: List<ScheduleEntry> = emptyList(),
    val selectedDay: String = currentDayName(),
    val error: String? = null
) {
    val entriesForDay: List<ScheduleEntry>
        get() = schedule.filter { it.dayOfWeek.equals(selectedDay, ignoreCase = true) }.sortedBy { it.time ?: "99:99" }
}

fun currentDayName(): String = LocalDate.now().dayOfWeek.getDisplayName(
    TextStyle.FULL,
    Locale.ENGLISH
)

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val getSchedule: GetScheduleUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScheduleUiState())
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()

    init {
        loadSchedule()
    }

    fun loadSchedule() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            when (val result = getSchedule()) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(
                        schedule = result.data,
                        isLoading = false,
                        error = null
                    )
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.message ?: "Failed to load schedule"
                    )
                }
                is Result.Loading -> {}
            }
        }
    }

    fun onDaySelect(day: String) {
        _uiState.value = _uiState.value.copy(selectedDay = day)
    }
}
