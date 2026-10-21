package com.anistream.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.anistream.core.database.entity.PlaylistEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY id")
    fun observeAll(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists ORDER BY id")
    suspend fun getAll(): List<PlaylistEntity>

    @Insert
    suspend fun insert(entity: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)
}
