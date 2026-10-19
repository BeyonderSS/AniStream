package com.anistream.feature.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anistream.core.common.Result
import com.anistream.domain.model.FavoriteAnime
import com.anistream.domain.usecase.GetFavoritesUseCase
import com.anistream.domain.repository.FavoriteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SortOption(val label: String) {
    RECENT("Recently Added"),
    TITLE("Title A-Z"),
    SCORE("Highest Score"),
    LAST_WATCHED("Last Watched")
}

data class FavoritesUiState(
    val isLoading: Boolean = true,
    val favorites: List<FavoriteAnime> = emptyList(),
    val sortedFavorites: List<FavoriteAnime> = emptyList(),
    val sortOption: SortOption = SortOption.RECENT,
    val error: String? = null
)

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val getFavorites: GetFavoritesUseCase,
    private val favoriteRepository: FavoriteRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FavoritesUiState())
    val uiState: StateFlow<FavoritesUiState> = _uiState.asStateFlow()

    // loaded by the screen's resume effect (also fires on first show)

    fun loadFavorites() {
        viewModelScope.launch {
            // silent refresh on resume once we have data
            _uiState.value = _uiState.value.copy(isLoading = _uiState.value.favorites.isEmpty(), error = null)
            when (val result = getFavorites()) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(
                        favorites = result.data,
                        isLoading = false,
                        error = null
                    )
                    applySort()
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.message
                    )
                }
                is Result.Loading -> {}
            }
        }
    }

    fun onSortChange(option: SortOption) {
        _uiState.value = _uiState.value.copy(sortOption = option)
        applySort()
    }

    /** Optimistic: drops it from the grid now; [restoreFavorite] undoes. */
    fun removeFavorite(favorite: FavoriteAnime) {
        _uiState.value = _uiState.value.copy(favorites = _uiState.value.favorites.filterNot { it.animeSlug == favorite.animeSlug })
        applySort()
        viewModelScope.launch { favoriteRepository.remove(favorite.animeSlug) }
    }

    fun restoreFavorite(favorite: FavoriteAnime) {
        viewModelScope.launch {
            favoriteRepository.add(favorite)
            loadFavorites()
        }
    }

    private fun applySort() {
        val sorted = when (_uiState.value.sortOption) {
            SortOption.RECENT -> _uiState.value.favorites.sortedByDescending { it.addedTimestamp }
            SortOption.TITLE -> _uiState.value.favorites.sortedBy { it.animeTitle.lowercase() }
            SortOption.SCORE -> _uiState.value.favorites.sortedByDescending { it.score ?: 0.0 }
            SortOption.LAST_WATCHED -> _uiState.value.favorites.sortedByDescending { it.lastEpisodeWatched }
        }
        _uiState.value = _uiState.value.copy(sortedFavorites = sorted)
    }
}
