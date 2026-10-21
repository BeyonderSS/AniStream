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
    val anidbSlug: String? = null,
    /** "anime" | "media". Media rows: animeSlug = history key ("mb:<id>"), lastEpisodeWatched = episode (0 for movies). */
    val kind: String = "anime",
    val season: Int = 0,
    val mediaId: String? = null,
    /** e.g. "S1 · E2" */
    val subtitle: String? = null
) {
    val nextEpisode: Int get() = lastEpisodeWatched + 1
    val isComplete: Boolean get() = totalEpisodes > 0 && lastEpisodeWatched >= totalEpisodes
}
