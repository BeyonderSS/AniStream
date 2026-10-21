package com.anistream.domain.model

/**
 * Catalog-independent history slug for a movie/series: "<movie|series>:<normalized title>" (the sync key is "media:" + it).
 * Must match web/src/lib/core.ts mediaSlug byte for byte.
 * ponytail: same-title remakes collide; include the year when every source provides it.
 */
fun mediaSlug(series: Boolean, title: String): String =
    (if (series) "series:" else "movie:") + title.lowercase().replace("&", "and").replace(Regex("^\\s*the\\b"), "").replace(Regex("[^\\p{L}\\p{N}]"), "")

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
    /** "anime" | "media". Media rows: animeSlug = mediaSlug(), mediaId = id used to open it, lastEpisodeWatched = episode (0 for movies). */
    val kind: String = "anime",
    val season: Int = 0,
    val mediaId: String? = null,
    /** e.g. "S1 · E2" */
    val subtitle: String? = null
) {
    val nextEpisode: Int get() = lastEpisodeWatched + 1
    val isComplete: Boolean get() = totalEpisodes > 0 && lastEpisodeWatched >= totalEpisodes
}
