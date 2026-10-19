package com.anistream.core.media.moviebox

import com.anistream.domain.model.MediaDetail
import com.anistream.domain.model.MediaItem
import com.anistream.domain.model.MediaStream

/**
 * MovieBox provider (search, detail, streams). Implemented in [MovieBoxClient].
 * Ids passed in and out are raw MovieBox subjectIds; MediaItem/MediaDetail ids are "mb:<subjectId>".
 * Every function throws on failure (callers wrap in Result).
 */
interface MovieBoxSource {
    suspend fun search(query: String, page: Int = 1): List<MediaItem>

    suspend fun detail(subjectId: String): MediaDetail

    /** Direct signed MP4s, best first, each carrying its subtitle tracks. Movies: season = episode = 0. */
    suspend fun streams(subjectId: String, season: Int, episode: Int): List<MediaStream>
}
