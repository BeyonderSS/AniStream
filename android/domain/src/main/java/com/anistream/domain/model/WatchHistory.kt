package com.anistream.domain.model

/**
 * Catalog-independent history slug for a movie/series: "<movie|series>:<normalized title>" (the sync key is "media:" + it).
 * Must match web/src/lib/core.ts mediaSlug byte for byte.
 * ponytail: same-title remakes collide; include the year when every source provides it.
 */
fun mediaSlug(series: Boolean, title: String): String =
    (if (series) "series:" else "movie:") + title.lowercase().replace("&", "and").replace(Regex("^\\s*the\\b"), "").replace(Regex("[^\\p{L}\\p{N}]"), "")

data class WatchHistory(
    val animeSlug: String,
    val animeTitle: String,
    val malId: Long? = null,
    val posterUrl: String? = null,
    val totalEpisodes: Int = 0,
    val lastEpisodeWatched: Int = 0,
    val watchProgress: Float = 0f,
    val lastWatchedTimestamp: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val anidbSlug: String? = null,
    /** "anime" | "media". Media rows: animeSlug = mediaSlug(), mediaId = id used to open it, lastEpisodeWatched = episode (0 for movies). */
    val kind: String = "anime",
    val season: Int = 0,
    val mediaId: String? = null,
    /** e.g. "S1 · E2" */
    val subtitle: String? = null,
    /** per-episode progress ([Eps.key] -> p/at); the row itself stays "latest episode" */
    val eps: Map<String, EpProgress> = emptyMap()
) {
    val nextEpisode: Int get() = lastEpisodeWatched + 1
    val isComplete: Boolean get() = totalEpisodes > 0 && lastEpisodeWatched >= totalEpisodes
}

/** One episode's progress: [p] = fraction 0..1, [at] = client ms when it was written (the per-episode merge clock). */
data class EpProgress(val p: Float, val at: Long)

object Eps {
    /** Episodes at or past this fraction count as watched (same 90% as the web grid). */
    const val DONE = 0.9f

    /** Wire key: anime `e12`, series `s5e1`; movies have none. Same keys as web/src/lib/eps.ts. */
    fun key(kind: String, season: Int, episode: Int): String? = when {
        episode <= 0 -> null
        kind == "anime" -> "e$episode"
        season > 0 -> "s${season}e$episode"
        else -> null
    }

    /** Per episode, the newer `at` wins; a tie keeps [b] (the incoming one). */
    fun merge(a: Map<String, EpProgress>, b: Map<String, EpProgress>): Map<String, EpProgress> =
        (a.keys + b.keys).associateWith { k -> val x = a[k]; val y = b[k]; if (x == null) y!! else if (y == null || x.at > y.at) x else y }

    /** Room column text `key:p:at;...` (keys never contain ':' or ';'). */
    fun format(m: Map<String, EpProgress>) = m.entries.joinToString(";") { "${it.key}:${it.value.p}:${it.value.at}" }
    fun parse(s: String): Map<String, EpProgress> = s.split(';').mapNotNull { part ->
        val f = part.split(':')
        if (f.size == 3) f[1].toFloatOrNull()?.let { p -> f[2].toLongOrNull()?.let { at -> f[0] to EpProgress(p, at) } } else null
    }.toMap()

    /** Wire progress is permille of 1000 (API.md); this is the one conversion each way. */
    fun toPermille(fraction: Float) = (fraction.coerceIn(0f, 1f) * 1000).toLong()
    fun fromPermille(position: Long, duration: Long) = if (duration <= 0) 0f else (position.toFloat() / duration).coerceIn(0f, 1f)
}
