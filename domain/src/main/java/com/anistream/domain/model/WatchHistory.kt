package com.anistream.domain.model

data class WatchHistory(
    val animeSlug: String,
    val animeTitle: String,
    val malId: Long? = null,
    val posterUrl: String? = null,
    val totalEpisodes: Int = 0,
    val lastEpisodeWatched: Int = 0,
    val watchProgress: Float = 0f,
    val lastWatchedTimestamp: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val anidbSlug: String? = null
) {
    val nextEpisode: Int get() = lastEpisodeWatched + 1
    val isComplete: Boolean get() = totalEpisodes > 0 && lastEpisodeWatched >= totalEpisodes
}
