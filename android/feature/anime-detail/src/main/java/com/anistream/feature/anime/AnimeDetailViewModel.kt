package com.anistream.feature.anime

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anistream.core.common.Result
import com.anistream.domain.model.AnimeDetail
import com.anistream.domain.model.Episode
import com.anistream.domain.model.FavoriteAnime
import com.anistream.domain.model.WatchHistory
import com.anistream.domain.repository.FavoriteRepository
import com.anistream.domain.repository.WatchHistoryRepository
import com.anistream.domain.usecase.GetAnimeDetailUseCase
import com.anistream.domain.usecase.GetEpisodesUseCase
import com.anistream.domain.usecase.ToggleFavoriteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AnimeDetailUiState(
    val isLoading: Boolean = true,
    val anime: AnimeDetail? = null,
    val episodes: List<Episode> = emptyList(),
    val episodesError: String? = null,
    val isFavorite: Boolean = false,
    val history: WatchHistory? = null,
    val error: String? = null
)

@HiltViewModel
class AnimeDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getAnimeDetail: GetAnimeDetailUseCase,
    private val getEpisodes: GetEpisodesUseCase,
    private val toggleFavorite: ToggleFavoriteUseCase,
    private val favoriteRepository: FavoriteRepository,
    private val watchHistoryRepository: WatchHistoryRepository
) : ViewModel() {

    val slug: String = savedStateHandle.get<String>("slug") ?: ""

    private val _uiState = MutableStateFlow(AnimeDetailUiState())
    val uiState: StateFlow<AnimeDetailUiState> = _uiState.asStateFlow()

    init {
        loadAnimeDetail()
        viewModelScope.launch {
            val fav = favoriteRepository.isFavorite(slug).getOrNull() ?: false
            _uiState.update { it.copy(isFavorite = fav) }
        }
    }

    fun loadAnimeDetail() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, episodesError = null) }
            val episodesDeferred = async { getEpisodes(slug) }
            val detailResult = getAnimeDetail(slug)
            val episodesResult = episodesDeferred.await()
            val detail = detailResult.getOrNull()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    anime = detail,
                    episodes = episodesResult.getOrNull() ?: emptyList(),
                    episodesError = episodesResult.exceptionOrNull()?.let { e -> e.message ?: "Failed to load episodes" },
                    error = if (detail == null) detailResult.exceptionOrNull()?.message ?: "Failed to load" else null
                )
            }
        }
    }

    /** Called on resume so the Continue button reflects progress made in the player. */
    fun refreshHistory() {
        viewModelScope.launch {
            val h = watchHistoryRepository.getBySlug(slug).getOrNull()
            _uiState.update { it.copy(history = h) }
        }
    }

    fun toggleFavorite() {
        val anime = _uiState.value.anime ?: return
        viewModelScope.launch {
            val result = toggleFavorite(
                FavoriteAnime(
                    animeSlug = slug,
                    animeTitle = anime.title,
                    posterUrl = anime.posterUrl,
                    score = anime.score,
                    totalEpisodes = anime.episodes ?: _uiState.value.episodes.size,
                    lastEpisodeWatched = _uiState.value.history?.lastEpisodeWatched ?: 0
                )
            )
            if (result is Result.Success) {
                _uiState.update { it.copy(isFavorite = result.data) }
            }
        }
    }
}
