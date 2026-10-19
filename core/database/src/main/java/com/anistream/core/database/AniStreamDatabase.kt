package com.anistream.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.anistream.core.database.dao.DownloadDao
import com.anistream.core.database.dao.FavoriteDao
import com.anistream.core.database.dao.RecentSearchDao
import com.anistream.core.database.dao.WatchHistoryDao
import com.anistream.core.database.entity.DownloadEntity
import com.anistream.core.database.entity.FavoriteEntity
import com.anistream.core.database.entity.RecentSearchEntity
import com.anistream.core.database.entity.WatchHistoryEntity

@Database(
    entities = [
        WatchHistoryEntity::class,
        FavoriteEntity::class,
        RecentSearchEntity::class,
        DownloadEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AniStreamDatabase : RoomDatabase() {
    abstract fun watchHistoryDao(): WatchHistoryDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun recentSearchDao(): RecentSearchDao
    abstract fun downloadDao(): DownloadDao

    companion object {
        const val DATABASE_NAME = "anistream.db"
    }
}
