package com.anistream.core.network.repository

import com.anistream.core.common.Result
import com.anistream.core.database.dao.FavoriteDao
import com.anistream.core.database.entity.FavoriteEntity
import com.anistream.domain.model.FavoriteAnime
import com.anistream.domain.repository.FavoriteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoriteRepositoryImpl @Inject constructor(
    private val dao: FavoriteDao
) : FavoriteRepository {

    override suspend fun getAll(): Result<List<FavoriteAnime>> {
        return try {
            val result = dao.getAll().first().map { it.toDomain() }
            Result.success(result)
        } catch (e: Exception) {
            Result.error(e)
        }
    }

    override suspend fun isFavorite(slug: String): Result<Boolean> {
        return try {
            Result.success(dao.isFavorite(slug))
        } catch (e: Exception) {
            Result.error(e)
        }
    }

    override suspend fun toggle(favorite: FavoriteAnime): Result<Boolean> {
        return try {
            val isFav = dao.isFavorite(favorite.animeSlug)
            if (isFav) {
                dao.delete(favorite.animeSlug)
                Result.success(false)
            } else {
                dao.upsert(favorite.toEntity())
                Result.success(true)
            }
        } catch (e: Exception) {
            Result.error(e)
        }
    }

    override suspend fun add(favorite: FavoriteAnime): Result<Unit> {
        return try {
            dao.upsert(favorite.toEntity())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.error(e)
        }
    }

    override suspend fun remove(slug: String): Result<Unit> {
        return try {
            dao.delete(slug)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.error(e)
        }
    }

    private fun FavoriteEntity.toDomain() = FavoriteAnime(
        animeSlug = animeSlug,
        malId = malId,
        animeTitle = animeTitle,
        posterUrl = posterUrl,
        score = score,
        addedTimestamp = addedTimestamp,
        totalEpisodes = totalEpisodes,
        lastEpisodeWatched = lastEpisodeWatched
    )

    private fun FavoriteAnime.toEntity() = FavoriteEntity(
        animeSlug = animeSlug,
        malId = malId,
        animeTitle = animeTitle,
        posterUrl = posterUrl,
        score = score,
        addedTimestamp = addedTimestamp,
        totalEpisodes = totalEpisodes,
        lastEpisodeWatched = lastEpisodeWatched
    )
}
