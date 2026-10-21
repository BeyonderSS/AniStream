package com.anistream.domain.model

data class StreamSource(
    val qualityLabel: String,
    val m3u8Url: String,
    val width: Int = 0,
    val height: Int = 0,
    val bandwidth: Long = 0,
    // the stream and subtitle hosts 403 without the embed site as Referer
    val referer: String? = null,
    val subtitleUrl: String? = null,
    /** MyAnimeList id from the embed url ("/mal/<id>/"), for AniSkip */
    val malId: Long? = null
) : Comparable<StreamSource> {
    override fun compareTo(other: StreamSource): Int {
        return other.height.compareTo(this.height)
    }
}

/** An AniSkip interval in the episode; [type] "op" | "mixed-op" | "recap" | "ed" | "mixed-ed". */
data class SkipInterval(val startMs: Long, val endMs: Long, val type: String) {
    val isIntroOrRecap: Boolean get() = type == "op" || type == "mixed-op" || type == "recap"
}
