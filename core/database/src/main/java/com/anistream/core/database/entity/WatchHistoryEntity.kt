package com.anistream.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watch_history")
data class WatchHistoryEntity(
    @PrimaryKey val animeSlug: String,
    val animeTitle: String,
    val malId: Long? = null,
    val posterUrl: String? = null,
    val totalEpisodes: Int = 0,
    val lastEpisodeWatched: Int = 0,
    val watchProgress: Float = 0f,
    val lastWatchedTimestamp: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val anidbSlug: String? = null,
    /** "anime" | "media" (movies & TV, keyed "mb:<id>") */
    @ColumnInfo(defaultValue = "'anime'") val kind: String = "anime",
    @ColumnInfo(defaultValue = "0") val season: Int = 0,
    val mediaId: String? = null,
    /** e.g. "S1 · E2" */
    val subtitle: String? = null
)
