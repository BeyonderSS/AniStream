package com.anistream.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anistream.core.account.HistorySync
import com.anistream.domain.model.Recommendation
import com.anistream.domain.model.WatchHistory
import com.anistream.domain.repository.RecommendationsRepository
import com.anistream.domain.repository.HomeContent
import com.anistream.domain.usecase.GetContinueWatchingUseCase
import com.anistream.domain.usecase.GetHomeContentUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = true,
    val continueWatching: List<WatchHistory> = emptyList(),
    val content: HomeContent = HomeContent(),
    val error: String? = null,
    /** ranked "For you" anime from the Room cache; empty without history */
    val picks: List<Recommendation> = emptyList()
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getContinueWatching: GetContinueWatchingUseCase,
    private val getHomeContent: GetHomeContentUseCase,
    recommendations: RecommendationsRepository,
    sync: HistorySync
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadContent()
        // a history pull from the account changes Continue Watching
        viewModelScope.launch { sync.pulled.collect { refreshContinueWatching() } }
        viewModelScope.launch {
            recommendations.observe("anime").collect { picks -> _uiState.update { it.copy(picks = picks) } }
        }
    }

    fun loadContent() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = getHomeContent()
            val content = result.getOrNull()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    content = content ?: it.content,
                    error = if (content == null) result.exceptionOrNull()?.message ?: "Failed to load home" else null
                )
            }
        }
        refreshContinueWatching()
    }

    /** Called on every resume so progress saved by the player shows up immediately. */
    fun refreshContinueWatching() {
        viewModelScope.launch {
            val list = getContinueWatching().getOrNull() ?: return@launch
            _uiState.update { it.copy(continueWatching = list) }
        }
    }
}
