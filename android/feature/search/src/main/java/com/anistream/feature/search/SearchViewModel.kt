package com.anistream.feature.search

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anistream.core.common.Result
import com.anistream.domain.model.AnimeSearchResult
import com.anistream.domain.model.MediaItem
import com.anistream.domain.model.MediaType
import com.anistream.domain.repository.AnidbRepository
import com.anistream.domain.repository.MoviesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val results: List<AnimeSearchResult> = emptyList(),
    val recentSearches: List<String> = emptyList(),
    val popular: List<AnimeSearchResult> = emptyList(),
    val isPopularLoading: Boolean = true,
    val typeFilter: String? = null,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val endReached: Boolean = false,
    val page: Int = 1,
    val error: String? = null,
    /** 0 = anime, 1 = movies & TV */
    val scope: Int = 0,
    val mediaPopular: List<AnimeSearchResult> = emptyList(),
    val isMediaPopularLoading: Boolean = true
) {
    val visibleResults: List<AnimeSearchResult>
        get() = typeFilter?.let { f -> results.filter { it.type.equals(f, ignoreCase = true) } } ?: results
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: AnidbRepository,
    private val movies: MoviesRepository,
    @ApplicationContext context: Context
) : ViewModel() {

    private val prefs = context.getSharedPreferences("search_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            val popular = repository.browseCategory("most-popular").getOrNull().orEmpty().distinctBy { it.slug }
            _uiState.update { it.copy(popular = popular, isPopularLoading = false) }
        }
    }

    private var mediaPopularRequested = false

    init {
        // the Anime | Movies & TV choice survives restarts
        onScopeChange(prefs.getInt(KEY_SCOPE, 0))
    }

    fun onScopeChange(scope: Int) {
        if (scope == _uiState.value.scope) return
        prefs.edit().putInt(KEY_SCOPE, scope).apply()
        _uiState.update { it.copy(scope = scope, typeFilter = null, results = emptyList()) }
        if (scope == 1 && !mediaPopularRequested) {
            mediaPopularRequested = true
            viewModelScope.launch {
                val first = movies.home().getOrNull()?.firstOrNull()?.items.orEmpty().map { it.asCard() }.distinctBy { it.slug }
                _uiState.update { it.copy(mediaPopular = first, isMediaPopularLoading = false) }
            }
        }
        onQueryChange(_uiState.value.query)
    }

    /** Both scopes land in the same poster grid; movie ids ride in [AnimeSearchResult.slug]. */
    private suspend fun search(query: String, page: Int): Result<List<AnimeSearchResult>> =
        if (_uiState.value.scope == 1) movies.search(query, page).map { list -> list.map { it.asCard() } }
        else repository.searchAnime(query, page)

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(results = emptyList(), isLoading = false, error = null) }
            return
        }
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            delay(400)
            when (val result = search(query.trim(), 1)) {
                is Result.Success -> _uiState.update { s ->
                    s.copy(
                        results = result.data.distinctBy { it.slug },
                        isLoading = false,
                        page = 1,
                        endReached = result.data.isEmpty(),
                        recentSearches = (listOf(query.trim()) + s.recentSearches.filterNot { it.equals(query.trim(), true) }).take(10)
                    )
                }
                is Result.Error -> _uiState.update { it.copy(isLoading = false, error = result.message ?: "Search failed") }
                is Result.Loading -> {}
            }
        }
    }

    fun loadNextPage() {
        val s = _uiState.value
        if (s.isLoading || s.isLoadingMore || s.endReached || s.query.isBlank()) return
        val next = s.page + 1
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            val more = search(s.query.trim(), next).getOrNull()
            _uiState.update { st ->
                if (more == null) {
                    st.copy(isLoadingMore = false, endReached = true)
                } else {
                    val merged = (st.results + more).distinctBy { it.slug }
                    st.copy(
                        results = merged,
                        page = next,
                        isLoadingMore = false,
                        // a page that adds nothing new means we're past the end
                        endReached = merged.size == st.results.size
                    )
                }
            }
        }
    }

    fun onClearQuery() = onQueryChange("")

    fun onTypeFilterChange(type: String?) = _uiState.update { it.copy(typeFilter = type) }

    fun onRecentSearchClick(query: String) = onQueryChange(query)

    fun onRemoveRecent(query: String) = _uiState.update { it.copy(recentSearches = it.recentSearches - query) }

    fun onClearRecents() = _uiState.update { it.copy(recentSearches = emptyList()) }
}

private fun MediaItem.asCard() = AnimeSearchResult(
    slug = id,
    title = title,
    posterUrl = posterUrl,
    type = if (type == MediaType.SERIES) "Series" else "Movie",
    score = rating,
    year = year
)

private const val KEY_SCOPE = "scope"
