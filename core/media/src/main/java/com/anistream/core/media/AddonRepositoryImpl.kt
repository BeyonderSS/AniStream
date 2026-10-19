package com.anistream.core.media

import com.anistream.core.common.Result
import com.anistream.core.common.safeApiCall
import com.anistream.core.database.dao.AddonDao
import com.anistream.core.database.entity.AddonEntity
import com.anistream.domain.model.Addon
import com.anistream.domain.repository.AddonRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AddonRepositoryImpl @Inject constructor(
    private val http: MediaHttp,
    private val dao: AddonDao
) : AddonRepository {

    override fun addons(): Flow<List<Addon>> = dao.observeAll()
        .onStart { dao.insertIfAbsent(CINEMETA) }
        .map { list -> list.map { Addon(it.id, it.name, it.manifestUrl, it.description, it.enabled, it.builtIn) } }

    override suspend fun add(manifestUrl: String): Result<Addon> = safeApiCall {
        val url = normalizeManifestUrl(manifestUrl)
        val m = Stremio.parseManifest(http.getJson(url))
        val builtIn = m.id == Stremio.CINEMETA_ID
        val entity = AddonEntity(
            id = m.id,
            name = m.name,
            manifestUrl = if (builtIn) Stremio.CINEMETA_MANIFEST else url,
            description = m.description,
            enabled = true,
            builtIn = builtIn,
            resourcesCsv = m.resources.joinToString(","),
            typesCsv = m.types.joinToString(",")
        )
        dao.upsert(entity)
        Addon(entity.id, entity.name, entity.manifestUrl, entity.description, entity.enabled, entity.builtIn)
    }

    override suspend fun remove(id: String): Result<Unit> = safeApiCall {
        require(id != Stremio.CINEMETA_ID) { "Cinemeta is built in and can't be removed" }
        dao.delete(id)
    }

    override suspend fun setEnabled(id: String, enabled: Boolean): Result<Unit> = safeApiCall {
        require(id != Stremio.CINEMETA_ID || enabled) { "Cinemeta is built in and always enabled" }
        dao.setEnabled(id, enabled)
    }

    companion object {
        private val CINEMETA = AddonEntity(
            id = Stremio.CINEMETA_ID,
            name = "Cinemeta",
            manifestUrl = Stremio.CINEMETA_MANIFEST,
            description = "The official addon for movie and series catalogs",
            enabled = true,
            builtIn = true,
            resourcesCsv = "catalog,meta,addon_catalog",
            typesCsv = "movie,series"
        )

        /** "stremio://host/x/manifest.json" → "https://host/x/manifest.json"; must point at a manifest.json. */
        internal fun normalizeManifestUrl(raw: String): String {
            val url = raw.trim().let { if (it.startsWith("stremio://")) "https://" + it.removePrefix("stremio://") else it }
            require(url.startsWith("https://") || url.startsWith("http://")) { "Addon URL must be https:// or stremio://" }
            require(url.substringBefore('?').endsWith("/manifest.json")) { "Addon URL must end with /manifest.json" }
            return url
        }
    }
}
