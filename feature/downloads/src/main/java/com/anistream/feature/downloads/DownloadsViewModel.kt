package com.anistream.feature.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anistream.core.database.dao.DownloadDao
import com.anistream.domain.model.DownloadItem
import com.anistream.domain.model.DownloadState
import com.anistream.domain.repository.DownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DownloadsUiState(
    val isLoading: Boolean = true,
    val downloads: List<DownloadItem> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    dao: DownloadDao,
    private val downloadRepository: DownloadRepository
) : ViewModel() {

    // DownloadRepository only exposes a one-shot getAll(); the DAO Flow gives live progress
    val uiState: StateFlow<DownloadsUiState> = dao.getAll()
        .map { rows ->
            DownloadsUiState(
                isLoading = false,
                downloads = rows.map {
                    DownloadItem(
                        episodeId = it.episodeId,
                        animeTitle = it.animeTitle,
                        episodeNumber = it.episodeNumber,
                        quality = it.quality,
                        filePath = it.filePath,
                        fileSizeBytes = it.fileSizeBytes,
                        progress = it.progress,
                        state = runCatching { DownloadState.valueOf(it.state) }.getOrDefault(DownloadState.PENDING),
                        createdAt = it.createdAt
                    )
                }
            )
        }
        .catch { emit(DownloadsUiState(isLoading = false, error = it.message ?: "Failed to load downloads")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadsUiState())

    fun deleteDownload(episodeId: Long) {
        viewModelScope.launch { downloadRepository.deleteDownload(episodeId) }
    }

    fun cancelDownload(episodeId: Long) {
        viewModelScope.launch { downloadRepository.cancelDownload(episodeId) }
    }
}
