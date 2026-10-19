package com.anistream.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.anistream.core.database.dao.AddonDao
import com.anistream.core.database.dao.DownloadDao
import com.anistream.core.database.dao.FavoriteDao
import com.anistream.core.database.dao.PlaylistDao
import com.anistream.core.database.dao.RecentSearchDao
import com.anistream.core.database.dao.WatchHistoryDao
import com.anistream.core.database.entity.AddonEntity
import com.anistream.core.database.entity.DownloadEntity
import com.anistream.core.database.entity.FavoriteEntity
import com.anistream.core.database.entity.PlaylistEntity
import com.anistream.core.database.entity.RecentSearchEntity
import com.anistream.core.database.entity.WatchHistoryEntity

@Database(
    entities = [
        WatchHistoryEntity::class,
        FavoriteEntity::class,
        RecentSearchEntity::class,
        DownloadEntity::class,
        AddonEntity::class,
        PlaylistEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AniStreamDatabase : RoomDatabase() {
    abstract fun watchHistoryDao(): WatchHistoryDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun recentSearchDao(): RecentSearchDao
    abstract fun downloadDao(): DownloadDao
    abstract fun addonDao(): AddonDao
    abstract fun playlistDao(): PlaylistDao

    companion object {
        const val DATABASE_NAME = "anistream.db"

        /** v2: Movies & TV history/download columns (+ addon/playlist tables). Keeps existing users' history. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE watch_history ADD COLUMN kind TEXT NOT NULL DEFAULT 'anime'")
                db.execSQL("ALTER TABLE watch_history ADD COLUMN season INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE watch_history ADD COLUMN mediaId TEXT")
                db.execSQL("ALTER TABLE watch_history ADD COLUMN subtitle TEXT")
                db.execSQL("ALTER TABLE downloads ADD COLUMN subtitle TEXT")
                db.execSQL("CREATE TABLE IF NOT EXISTS `addons` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `manifestUrl` TEXT NOT NULL, `description` TEXT, `enabled` INTEGER NOT NULL, `builtIn` INTEGER NOT NULL, `resourcesCsv` TEXT NOT NULL, `typesCsv` TEXT NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `playlists` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `url` TEXT NOT NULL)")
            }
        }
    }
}
