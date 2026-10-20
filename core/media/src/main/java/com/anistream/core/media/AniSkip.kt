package com.anistream.core.media

import com.anistream.domain.model.SkipInterval
import com.anistream.domain.repository.SkipTimesRepository
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/** Public AniSkip API (no key): intro / recap / credits times per MAL id + episode. */
@Singleton
class AniSkip @Inject constructor(private val http: MediaHttp) : SkipTimesRepository {

    // ponytail: in-memory per process; sub/dub switches and retries don't refetch
    private val cache = ConcurrentHashMap<String, List<SkipInterval>>()

    override suspend fun skipTimes(malId: Long, episode: Int): List<SkipInterval> {
        val key = "$malId/$episode"
        cache[key]?.let { return it }
        val url = "https://api.aniskip.com/v2/skip-times/$malId/$episode" +
            "?types[]=op&types[]=recap&types[]=ed&types[]=mixed-op&episodeLength=0"
        return try {
            parse(http.get(url)).also { cache[key] = it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList() // 404 = no data for this episode; offline = same as no data
        }
    }

    companion object {
        /**
         * results[{interval{startTime,endTime}, skipType}] (seconds) → intervals sorted by start.
         * One interval per type (the API lists the best-voted first); "mixed-op" is dropped when a plain "op" exists.
         */
        fun parse(json: String): List<SkipInterval> {
            val results = MediaHttp.json.parseToJsonElement(json).jsonObject["results"] as? JsonArray ?: return emptyList()
            val all = results.mapNotNull { r ->
                val o = r as? JsonObject ?: return@mapNotNull null
                val interval = o["interval"] as? JsonObject ?: return@mapNotNull null
                val start = (interval["startTime"] as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
                val end = (interval["endTime"] as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
                val type = (o["skipType"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                if (end <= start) null else SkipInterval((start * 1000).toLong(), (end * 1000).toLong(), type)
            }.distinctBy { it.type }
            val hasOp = all.any { it.type == "op" }
            return all.filterNot { hasOp && it.type == "mixed-op" }.sortedBy { it.startMs }
        }
    }
}
