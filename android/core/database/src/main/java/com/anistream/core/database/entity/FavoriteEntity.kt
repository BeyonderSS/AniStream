package com.anistream.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val animeSlug: String,
    val malId: Long? = null,
    val animeTitle: String,
    val posterUrl: String? = null,
    val score: Double? = null,
    val addedTimestamp: Long = System.currentTimeMillis(),
    val totalEpisodes: Int = 0,
    val lastEpisodeWatched: Int = 0
)
