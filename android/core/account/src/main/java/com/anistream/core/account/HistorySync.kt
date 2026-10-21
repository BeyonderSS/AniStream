package com.anistream.core.account

import android.content.Context
import com.anistream.core.database.dao.WatchHistoryDao
import com.anistream.core.database.entity.WatchHistoryEntity
import com.anistream.domain.model.WatchHistory
import com.anistream.domain.model.mediaSlug
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/** Wire entry of server/API.md. `position`/`duration` carry watch progress as permille (position / duration = fraction). */
data class SyncEntry(val key: String, val payload: JsonObject, val position: Long, val duration: Long, val updatedAt: Long, val deleted: Boolean)

object HistoryMerge {
    /** Last-write-wins: the remote entries that beat what we hold. [localUpdatedAt] = our updatedAt for a key, null if none. */
    fun toApply(remote: List<SyncEntry>, localUpdatedAt: (String) -> Long?): List<SyncEntry> =
        remote.filter { r -> localUpdatedAt(r.key).let { it == null || r.updatedAt > it } }
}

/** The one place a history row becomes the opaque `payload` (history sync and Continue-on-TV both use it). */
object HistoryPayload {
    fun key(h: WatchHistory) = "${h.kind}:${h.animeSlug}"

    /** Media rows saved under a catalog id ("tt…", "mb:…") rather than [mediaSlug]. */
    fun isLegacyMedia(h: WatchHistory) = h.kind == "media" && !h.animeSlug.startsWith("movie:") && !h.animeSlug.startsWith("series:")

    fun encode(h: WatchHistory): JsonObject = buildJsonObject {
        put("slug", h.animeSlug); put("title", h.animeTitle); put("kind", h.kind)
        put("malId", h.malId); put("poster", h.posterUrl); put("total", h.totalEpisodes)
        put("episode", h.lastEpisodeWatched); put("progress", h.watchProgress.toDouble())
        put("favorite", h.isFavorite); put("anidbSlug", h.anidbSlug)
        put("season", h.season); put("mediaId", h.mediaId); put("subtitle", h.subtitle)
    }

    fun decode(p: JsonObject, updatedAt: Long) = WatchHistory(
        animeSlug = str(p, "slug"), animeTitle = str(p, "title"),
        malId = p["malId"]?.jsonPrimitive?.longOrNull, posterUrl = p["poster"]?.jsonPrimitive?.contentOrNull,
        totalEpisodes = p["total"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
        lastEpisodeWatched = p["episode"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
        watchProgress = p["progress"]?.jsonPrimitive?.doubleOrNull?.toFloat() ?: 0f,
        lastWatchedTimestamp = updatedAt,
        isFavorite = p["favorite"]?.jsonPrimitive?.booleanOrNull ?: false,
        anidbSlug = p["anidbSlug"]?.jsonPrimitive?.contentOrNull,
        kind = str(p, "kind").ifEmpty { "anime" },
        season = p["season"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
        mediaId = p["mediaId"]?.jsonPrimitive?.contentOrNull, subtitle = p["subtitle"]?.jsonPrimitive?.contentOrNull
    )
}

private fun WatchHistoryEntity.toDomain() = WatchHistory(
    animeSlug, animeTitle, malId, posterUrl, totalEpisodes, lastEpisodeWatched, watchProgress,
    lastWatchedTimestamp, isFavorite, anidbSlug, kind, season, mediaId, subtitle
)

private fun WatchHistory.toEntity(profileId: String, deleted: Boolean = false) = WatchHistoryEntity(
    animeSlug, animeTitle, malId, posterUrl, totalEpisodes, lastEpisodeWatched, watchProgress,
    lastWatchedTimestamp, isFavorite, anidbSlug, kind, season, mediaId, subtitle, profileId, deleted
)

/** Pushes changed rows of the active profile and pulls what other devices wrote. Rows are keyed `<kind>:<slug>`; the entity's lastWatchedTimestamp is its updatedAt. */
@Singleton
class HistorySync @Inject constructor(
    @ApplicationContext context: Context,
    private val account: AccountRepository,
    private val api: AccountApi,
    private val dao: WatchHistoryDao
) {
    private val prefs = context.getSharedPreferences("history_sync", Context.MODE_PRIVATE)
    private val lock = Mutex()
    private val _pulled = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    /** fires after a pull changed local rows, so screens can reload Continue Watching */
    val pulled: SharedFlow<Unit> = _pulled.asSharedFlow()

    suspend fun pull(): Unit = lock.withLock {
        val s = account.session.value ?: return@withLock
        val profile = s.activeProfile?.id ?: return@withLock
        val adoptKey = "adopted:${s.user.id}"
        if (!prefs.getBoolean(adoptKey, false)) { // first sign-in on this device: offline history joins this profile
            dao.adoptLocal(profile)
            prefs.edit().putBoolean(adoptKey, true).apply()
        }
        val res = api.call("GET", "/v1/profiles/$profile/history?since=${prefs.getLong("since:$profile", 0L)}", token = s.token) ?: return@withLock
        val entries = res["entries"]?.jsonArray?.map { it.jsonObject.toEntry() }.orEmpty()
        val local = entries.mapNotNull { e -> localRow(profile, e.key)?.let { e.key to it.lastWatchedTimestamp } }.toMap()
        val wins = HistoryMerge.toApply(entries) { local[it] }
        wins.forEach { e -> applyEntry(profile, e) }
        collapseMedia(profile)
        res["now"]?.jsonPrimitive?.longOrNull?.let { prefs.edit().putLong("since:$profile", it).apply() }
        if (wins.isNotEmpty()) _pulled.tryEmit(Unit)
    }

    suspend fun push(): Unit = lock.withLock {
        val s = account.session.value ?: return@withLock
        val profile = s.activeProfile?.id ?: return@withLock
        collapseMedia(profile)
        val last = prefs.getLong("pushed:$profile", 0L)
        val rows = dao.changedSince(profile, last)
        if (rows.isEmpty()) return@withLock
        rows.chunked(100).forEach { chunk ->
            val body = buildJsonObject { put("entries", JsonArray(chunk.map { r -> r.toDomain().let { h -> entryJson(h, r.deleted) } })) }
            api.call("PUT", "/v1/profiles/$profile/history", body, s.token)
        }
        prefs.edit().putLong("pushed:$profile", rows.maxOf { it.lastWatchedTimestamp }).apply()
    }

    /** Re-key per-catalog-id media rows to [mediaSlug] (newest wins) and tombstone the old ones so other devices drop them too. */
    private suspend fun collapseMedia(profile: String) {
        val now = System.currentTimeMillis()
        dao.liveMedia(profile).map { it.toDomain() }.filter(HistoryPayload::isLegacyMedia).groupBy { mediaSlug(it.lastEpisodeWatched > 0 || it.season > 0, it.animeTitle) }.forEach { (slug, rows) ->
            val newest = rows.maxBy { it.lastWatchedTimestamp }
            val cur = dao.getRaw(slug, profile)
            if (cur == null || cur.deleted || cur.lastWatchedTimestamp < newest.lastWatchedTimestamp)
                dao.upsert(newest.copy(animeSlug = slug, lastWatchedTimestamp = now).toEntity(profile))
            rows.forEach { dao.delete(it.animeSlug, profile, now) }
        }
    }

    /** Continue on TV: the phone's row arrives as a payload; store it (newest wins) so the player resumes from it. */
    suspend fun applyPayload(profile: String, payload: JsonObject, progress: Float? = null): WatchHistory {
        val h = HistoryPayload.decode(payload, System.currentTimeMillis()).let { if (progress != null) it.copy(watchProgress = progress) else it }
        dao.upsert(h.toEntity(profile))
        _pulled.tryEmit(Unit)
        return h
    }

    suspend fun applyHandoff(h: Handoff): WatchHistory? =
        account.session.value?.activeProfile?.id?.let { applyPayload(it, h.payload) }

    private suspend fun localRow(profile: String, key: String) = key.substringAfter(':').let { dao.getRaw(it, profile) }

    private suspend fun applyEntry(profile: String, e: SyncEntry) {
        val h = HistoryPayload.decode(e.payload, e.updatedAt)
        dao.upsert(h.toEntity(profile, e.deleted).let { if (it.animeSlug.isEmpty()) it.copy(animeSlug = e.key.substringAfter(':')) else it })
    }

    private fun entryJson(h: WatchHistory, deleted: Boolean) = buildJsonObject {
        put("key", HistoryPayload.key(h)); put("payload", HistoryPayload.encode(h))
        put("position", (h.watchProgress * 1000).toLong()); put("duration", 1000L)
        put("updatedAt", h.lastWatchedTimestamp); put("deleted", deleted)
    }

    private fun JsonObject.toEntry() = SyncEntry(
        key = str(this, "key"), payload = this["payload"]?.jsonObject ?: JsonObject(emptyMap()),
        position = this["position"]?.jsonPrimitive?.longOrNull ?: 0, duration = this["duration"]?.jsonPrimitive?.longOrNull ?: 0,
        updatedAt = this["updatedAt"]?.jsonPrimitive?.longOrNull ?: 0, deleted = this["deleted"]?.jsonPrimitive?.booleanOrNull ?: false
    )
}
