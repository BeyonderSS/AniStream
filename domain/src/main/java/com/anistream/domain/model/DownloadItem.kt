package com.anistream.domain.model

data class DownloadItem(
    val episodeId: Long,
    val animeTitle: String,
    val episodeNumber: Int,
    val quality: String,
    val filePath: String? = null,
    val fileSizeBytes: Long = 0,
    val progress: Float = 0f,
    val state: DownloadState = DownloadState.PENDING,
    val createdAt: Long = System.currentTimeMillis()
)

enum class DownloadState {
    PENDING, DOWNLOADING, COMPLETED, FAILED, PAUSED
}
