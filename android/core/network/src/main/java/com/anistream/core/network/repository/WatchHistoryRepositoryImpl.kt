package com.anistream.core.network.repository

import com.anistream.core.common.Result
import com.anistream.core.database.ActiveProfile
import com.anistream.core.database.dao.WatchHistoryDao
import com.anistream.core.database.entity.WatchHistoryEntity
import com.anistream.domain.model.EpProgress
import com.anistream.domain.model.Eps
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
            dao.upsert(withEps(history).toEntity())
            ActiveProfile.changes.tryEmit(Unit)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.error(e)
        }
    }

    override suspend fun updateProgress(slug: String, episode: Int, progress: Float, durationMs: Long): Result<Unit> {
        return try {
            // no row yet (player didn't save metadata first) -> insert a minimal one so history is never lost
            val row = dao.getBySlug(slug, ActiveProfile.id)?.toDomain() ?: WatchHistory(animeSlug = slug, animeTitle = slug)
            dao.upsert(withEps(row.copy(lastEpisodeWatched = episode, watchProgress = progress, lastWatchedTimestamp = System.currentTimeMillis(), durationMs = durationMs)).toEntity())
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

    /**
     * Keep the stored per-episode map and stamp this episode's progress into it (0 = just opened, not recorded).
     * A row saved before the player knows the length keeps the stored one when it is the same episode.
     */
    private suspend fun withEps(h: WatchHistory): WatchHistory {
        val raw = dao.getRaw(h.animeSlug, ActiveProfile.id)
        val old = raw?.eps?.let(Eps::parse).orEmpty()
        val dur = h.durationMs.takeIf { it > 0 } ?: raw?.takeIf { it.season == h.season && it.lastEpisodeWatched == h.lastEpisodeWatched }?.durationMs ?: 0
        val k = Eps.key(h.kind, h.season, h.lastEpisodeWatched)
        val mine = if (k != null && h.watchProgress > 0f) mapOf(k to EpProgress(h.watchProgress, h.lastWatchedTimestamp)) else emptyMap()
        return h.copy(eps = Eps.merge(Eps.merge(old, h.eps), mine), durationMs = dur)
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
        subtitle = subtitle,
        eps = Eps.parse(eps),
        durationMs = durationMs
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
        profileId = ActiveProfile.id,
        eps = Eps.format(eps),
        durationMs = durationMs
    )
}
