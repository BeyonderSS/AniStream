package com.anistream.domain.model

data class AnimeSearchResult(
    val slug: String,
    val title: String,
    val posterUrl: String? = null,
    val type: String? = null,
    val score: Double? = null,
    val status: String? = null,
    val year: Int? = null,
    val season: String? = null,
    val malId: Long? = null
)
