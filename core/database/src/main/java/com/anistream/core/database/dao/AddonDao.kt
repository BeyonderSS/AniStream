package com.anistream.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.anistream.core.database.entity.AddonEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AddonDao {
    @Query("SELECT * FROM addons ORDER BY builtIn DESC, name")
    fun observeAll(): Flow<List<AddonEntity>>

    @Query("SELECT * FROM addons WHERE enabled = 1")
    suspend fun getEnabled(): List<AddonEntity>

    @Query("SELECT * FROM addons WHERE id = :id")
    suspend fun get(id: String): AddonEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entity: AddonEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AddonEntity)

    @Query("UPDATE addons SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)

    @Query("DELETE FROM addons WHERE id = :id")
    suspend fun delete(id: String)
}
