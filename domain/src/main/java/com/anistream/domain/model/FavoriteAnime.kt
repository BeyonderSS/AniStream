package com.anistream.domain.model

data class FavoriteAnime(
    val animeSlug: String,
    val malId: Long? = null,
    val animeTitle: String,
    val posterUrl: String? = null,
    val score: Double? = null,
    val addedTimestamp: Long = System.currentTimeMillis(),
    val totalEpisodes: Int = 0,
    val lastEpisodeWatched: Int = 0
)
