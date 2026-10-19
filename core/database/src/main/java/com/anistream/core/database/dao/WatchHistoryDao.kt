package com.anistream.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.anistream.core.database.entity.WatchHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchHistoryDao {
    @Query("SELECT * FROM watch_history ORDER BY lastWatchedTimestamp DESC")
    fun getAll(): Flow<List<WatchHistoryEntity>>

    /** movies (media rows with lastEpisodeWatched = 0) qualify until they're 95% watched */
    @Query("SELECT * FROM watch_history WHERE (lastEpisodeWatched > 0 OR (kind = 'media' AND watchProgress < 0.95)) AND (:kind IS NULL OR kind = :kind) ORDER BY lastWatchedTimestamp DESC")
    fun getContinueWatching(kind: String? = null): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history WHERE animeSlug = :slug")
    suspend fun getBySlug(slug: String): WatchHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WatchHistoryEntity)

    @Query("UPDATE watch_history SET lastEpisodeWatched = :episode, watchProgress = :progress, lastWatchedTimestamp = :timestamp WHERE animeSlug = :slug")
    suspend fun updateProgress(slug: String, episode: Int, progress: Float, timestamp: Long = System.currentTimeMillis()): Int

    @Query("DELETE FROM watch_history WHERE animeSlug = :slug")
    suspend fun delete(slug: String)

    @Query("DELETE FROM watch_history")
    suspend fun clear()
}
