package com.anistream.core.database.entity

import androidx.room.Entity

/**
 * A cached "For you" pick, in ranked [position] order per [kind] ("anime" | "media").
 *
 * Migration SQL (must match Room's generated schema exactly):
 * ```
 * CREATE TABLE IF NOT EXISTS `recommendations` (`kind` TEXT NOT NULL, `id` TEXT NOT NULL, `title` TEXT NOT NULL, `posterUrl` TEXT, `year` INTEGER, `rating` REAL, `mediaType` TEXT, `because` TEXT, `position` INTEGER NOT NULL, PRIMARY KEY(`kind`, `id`))
 * ```
 */
@Entity(tableName = "recommendations", primaryKeys = ["kind", "id"])
data class RecommendationEntity(
    val kind: String,
    val id: String,
    val title: String,
    val posterUrl: String?,
    val year: Int?,
    val rating: Double?,
    /** "MOVIE" | "SERIES" for media, null for anime */
    val mediaType: String?,
    val because: String?,
    val position: Int
)
