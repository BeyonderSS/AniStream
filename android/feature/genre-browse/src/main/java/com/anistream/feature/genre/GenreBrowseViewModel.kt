package com.anistream.feature.genre

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anistream.domain.model.AnimeSearchResult
import com.anistream.domain.model.Genre
import com.anistream.domain.repository.AnidbRepository
import com.anistream.domain.usecase.GetGenresUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GenreBrowseUiState(
    val isLoading: Boolean = true,
    val genres: List<Genre> = emptyList(),
    val error: String? = null,
    /** Category path being shown ("top-airing", "genres/action"); null = genre picker. */
    val path: String? = null,
    val title: String? = null,
    val results: List<AnimeSearchResult> = emptyList(),
    val page: Int = 0,
    val isResultsLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val endReached: Boolean = false,
    val resultsError: String? = null
)

@HiltViewModel
class GenreBrowseViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getGenres: GetGenresUseCase,
    private val repository: AnidbRepository
) : ViewModel() {

    /** Set when opened from Home "See all" / a detail genre chip. */
    val initialPath: String? = savedStateHandle.get<String>("path")?.takeIf { it.isNotBlank() }

    private val _uiState = MutableStateFlow(GenreBrowseUiState())
    val uiState: StateFlow<GenreBrowseUiState> = _uiState.asStateFlow()

    private var pageJob: Job? = null

    init {
        val p = initialPath
        if (p != null) openPath(p, titleFor(p)) else loadGenres()
    }

    fun loadGenres() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = getGenres()
            val genres = result.getOrNull()?.filterNot { ',' in it.name } // scraped junk like "Action, Adventure"
            _uiState.update {
                it.copy(
                    isLoading = false,
                    genres = genres ?: it.genres,
                    error = if (genres == null) result.exceptionOrNull()?.message ?: "Failed to load genres" else null
                )
            }
        }
    }

    fun onGenreClick(genre: Genre) {
        val slug = genre.slug.ifBlank { genre.name.lowercase().replace(' ', '-') }
        openPath("genres/$slug", genre.name)
    }

    private fun openPath(path: String, title: String) {
        pageJob?.cancel()
        _uiState.update {
            it.copy(
                path = path, title = title, results = emptyList(), page = 0,
                endReached = false, resultsError = null, isLoadingMore = false
            )
        }
        loadNextPage()
    }

    /** Loads page+1 of the current path; also serves as Retry. */
    fun loadNextPage() {
        val s = _uiState.value
        val path = s.path ?: return
        if (s.isResultsLoading || s.isLoadingMore || s.endReached) return
        val next = s.page + 1
        val first = next == 1
        pageJob = viewModelScope.launch {
            _uiState.update { it.copy(isResultsLoading = first, isLoadingMore = !first, resultsError = null) }
            val result = repository.browseCategory(path, next)
            val items = result.getOrNull()
            _uiState.update { st ->
                if (items == null) {
                    st.copy(
                        isResultsLoading = false, isLoadingMore = false,
                        resultsError = result.exceptionOrNull()?.message ?: "Failed to load",
                        // don't loop on a failing later page; the user can retry from the first page
                        endReached = !first
                    )
                } else {
                    val merged = (st.results + items).distinctBy { it.slug }
                    st.copy(
                        results = merged, page = next,
                        isResultsLoading = false, isLoadingMore = false,
                        endReached = merged.size == st.results.size
                    )
                }
            }
        }
    }

    /** Returns false when the screen should pop (opened directly on a category). */
    fun clearSelection(): Boolean {
        if (initialPath != null) return false
        pageJob?.cancel()
        _uiState.update { it.copy(path = null, title = null, results = emptyList(), resultsError = null) }
        if (_uiState.value.genres.isEmpty()) loadGenres()
        return true
    }

    private fun titleFor(path: String): String =
        path.substringAfterLast('/').split('-').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
}
