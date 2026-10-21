package com.anistream.core.network.repository

import com.anistream.core.common.Result
import com.anistream.core.database.ActiveProfile
import com.anistream.core.database.dao.WatchHistoryDao
import com.anistream.core.database.entity.WatchHistoryEntity
import com.anistream.domain.model.WatchHistory
import com.anistream.domain.repository.WatchHistoryRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchHistoryRepositoryImpl @Inject constructor(
    private val dao: WatchHistoryDao
) : WatchHistoryRepository {

    override suspend fun getAll(): Result<List<WatchHistory>> {
        return try {
            val result = dao.getAll(ActiveProfile.id).first().map { it.toDomain() }
            Result.success(result)
        } catch (e: Exception) {
            Result.error(e)
        }
    }

    override suspend fun getContinueWatching(kind: String?): Result<List<WatchHistory>> {
        return try {
            val result = dao.getContinueWatching(ActiveProfile.id, kind).first().map { it.toDomain() }
            Result.success(result)
        } catch (e: Exception) {
            Result.error(e)
        }
    }

    override suspend fun getBySlug(slug: String): Result<WatchHistory?> {
        return try {
            val entity = dao.getBySlug(slug, ActiveProfile.id)
            Result.success(entity?.toDomain())
        } catch (e: Exception) {
            Result.error(e)
        }
    }

    override suspend fun save(history: WatchHistory): Result<Unit> {
        return try {
            dao.upsert(history.toEntity())
            ActiveProfile.changes.tryEmit(Unit)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.error(e)
        }
    }

    override suspend fun updateProgress(slug: String, episode: Int, progress: Float): Result<Unit> {
        return try {
            // no row yet (player didn't save metadata first) -> insert a minimal one so history is never lost
            if (dao.updateProgress(slug, episode, progress, ActiveProfile.id) == 0) {
                dao.upsert(WatchHistoryEntity(animeSlug = slug, animeTitle = slug, lastEpisodeWatched = episode, watchProgress = progress, profileId = ActiveProfile.id))
            }
            ActiveProfile.changes.tryEmit(Unit)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.error(e)
        }
    }

    override suspend fun delete(slug: String): Result<Unit> {
        return try {
            dao.delete(slug, ActiveProfile.id)
            ActiveProfile.changes.tryEmit(Unit)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.error(e)
        }
    }

    override suspend fun clear(): Result<Unit> {
        return try {
            dao.clear(ActiveProfile.id)
            ActiveProfile.changes.tryEmit(Unit)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.error(e)
        }
    }

    private fun WatchHistoryEntity.toDomain() = WatchHistory(
        animeSlug = animeSlug,
        animeTitle = animeTitle,
        malId = malId,
        posterUrl = posterUrl,
        totalEpisodes = totalEpisodes,
        lastEpisodeWatched = lastEpisodeWatched,
        watchProgress = watchProgress,
        lastWatchedTimestamp = lastWatchedTimestamp,
        isFavorite = isFavorite,
        anidbSlug = anidbSlug,
        kind = kind,
        season = season,
        mediaId = mediaId,
        subtitle = subtitle
    )

    private fun WatchHistory.toEntity() = WatchHistoryEntity(
        animeSlug = animeSlug,
        animeTitle = animeTitle,
        malId = malId,
        posterUrl = posterUrl,
        totalEpisodes = totalEpisodes,
        lastEpisodeWatched = lastEpisodeWatched,
        watchProgress = watchProgress,
        lastWatchedTimestamp = lastWatchedTimestamp,
        isFavorite = isFavorite,
        anidbSlug = anidbSlug,
        kind = kind,
        season = season,
        mediaId = mediaId,
        subtitle = subtitle,
        profileId = ActiveProfile.id
    )
}
