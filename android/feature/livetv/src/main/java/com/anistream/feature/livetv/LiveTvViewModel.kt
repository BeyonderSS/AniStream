package com.anistream.feature.livetv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anistream.core.common.Result
import com.anistream.domain.model.Channel
import com.anistream.domain.model.liveRequest
import com.anistream.domain.model.Playlist
import com.anistream.domain.repository.LiveTvRepository
import com.anistream.domain.repository.NowPlaying
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

const val IPTV_ORG_URL = "https://iptv-org.github.io/iptv/index.m3u"

data class ChannelsState(
    val all: List<Channel> = emptyList(),
    val groups: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null
)

data class PlaylistAddState(val busy: Boolean = false, val error: String? = null, val done: Boolean = false)

@OptIn(FlowPreview::class)
@HiltViewModel
class LiveTvViewModel @Inject constructor(private val repo: LiveTvRepository) : ViewModel() {

    /** null until the DB emits, so we can show a skeleton instead of flashing the empty state. */
    val playlists: StateFlow<List<Playlist>?> = repo.playlists().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _channels = MutableStateFlow(ChannelsState())
    val channels: StateFlow<ChannelsState> = _channels.asStateFlow()

    val query = MutableStateFlow("")
    val group = MutableStateFlow<String?>(null)

    /** Filtering thousands of rows happens off the main thread, not in composition. */
    val visible: StateFlow<List<Channel>> = combine(
        _channels.map { it.all }.distinctUntilChanged(),
        query.debounce(200),
        group
    ) { all, q, g ->
        all.filter { c ->
            (g == null || c.group == g) && (q.isBlank() || c.name.contains(q.trim(), ignoreCase = true))
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _add = MutableStateFlow(PlaylistAddState())
    val add: StateFlow<PlaylistAddState> = _add.asStateFlow()

    private var lastPlaylists: List<Playlist>? = null

    init {
        viewModelScope.launch {
            playlists.collect { p ->
                if (p == null || p == lastPlaylists) return@collect
                val changed = lastPlaylists != null
                lastPlaylists = p
                // first emission: the repo's in-memory cache is fine; a playlist edit needs a re-fetch
                if (p.isEmpty()) _channels.value = ChannelsState() else load(refresh = changed)
            }
        }
    }

    fun load(refresh: Boolean = false) {
        viewModelScope.launch {
            _channels.update { it.copy(isLoading = it.all.isEmpty(), refreshing = it.all.isNotEmpty(), error = null) }
            when (val r = repo.channels(refresh)) {
                is Result.Success -> {
                    val groups = r.data.mapNotNull { it.group?.takeIf { g -> g.isNotBlank() } }.distinct().sorted()
                    _channels.value = ChannelsState(all = r.data, groups = groups)
                    if (group.value !in groups) group.value = null
                }
                is Result.Error -> _channels.update { it.copy(isLoading = false, refreshing = false, error = r.message ?: "Couldn't load channels") }
                Result.Loading -> Unit
            }
        }
    }

    fun play(c: Channel) {
        NowPlaying.request = c.liveRequest()
    }

    fun addPlaylist(name: String, url: String) {
        if (url.isBlank() || _add.value.busy) return
        viewModelScope.launch {
            _add.value = PlaylistAddState(busy = true)
            val n = name.ifBlank { url.substringAfter("://").substringBefore('/') }
            _add.value = when (val r = repo.addPlaylist(n.trim(), url.trim())) {
                is Result.Success -> PlaylistAddState(done = true)
                is Result.Error -> PlaylistAddState(error = r.message ?: "Couldn't add this playlist")
                Result.Loading -> PlaylistAddState()
            }
        }
    }

    fun resetAdd() { _add.value = PlaylistAddState() }

    fun removePlaylist(id: Long) = viewModelScope.launch { repo.removePlaylist(id) }
}
