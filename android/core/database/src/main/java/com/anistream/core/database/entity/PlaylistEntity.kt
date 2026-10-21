package com.anistream.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Saved M3U playlist for Live TV.
 *
 * Migration SQL (must match Room's generated schema exactly):
 * ```
 * CREATE TABLE IF NOT EXISTS `playlists` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `url` TEXT NOT NULL)
 * ```
 */
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val url: String
)
