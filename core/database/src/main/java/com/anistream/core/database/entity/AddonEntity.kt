package com.anistream.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Installed Stremio addon (Cinemeta is the built-in one).
 *
 * Migration SQL (must match Room's generated schema exactly):
 * ```
 * CREATE TABLE IF NOT EXISTS `addons` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `manifestUrl` TEXT NOT NULL, `description` TEXT, `enabled` INTEGER NOT NULL, `builtIn` INTEGER NOT NULL, `resourcesCsv` TEXT NOT NULL, `typesCsv` TEXT NOT NULL, PRIMARY KEY(`id`))
 * ```
 */
@Entity(tableName = "addons")
data class AddonEntity(
    @PrimaryKey val id: String,
    val name: String,
    val manifestUrl: String,
    val description: String?,
    val enabled: Boolean,
    val builtIn: Boolean,
    /** manifest resources, e.g. "catalog,meta,stream" */
    val resourcesCsv: String,
    /** manifest types, e.g. "movie,series" */
    val typesCsv: String
)
