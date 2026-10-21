package com.anistream.domain.model

data class ScheduleEntry(
    val animeSlug: String,
    val animeTitle: String,
    val posterUrl: String? = null,
    val episodeNumber: Int? = null,
    val time: String? = null,
    val dayOfWeek: String
)
