package com.anistream.domain.model

/** Movies & TV (MovieBox, Cinemeta, Stremio addons) and Live TV. Anime keeps its own models. */

enum class MediaType { MOVIE, SERIES }

/**
 * A browsable title. [id] is opaque to the UI and stable across launches:
 * "mb:<subjectId>" for MovieBox subjects, "tt…" (IMDb id) for Cinemeta items.
 * Pass it back to [com.anistream.domain.repository.MoviesRepository]; never parse it in the UI.
 */
data class MediaItem(
    val id: String,
    val type: MediaType,
    val title: String,
    val posterUrl: String? = null,
    val year: Int? = null,
    val rating: Double? = null,
    val genres: List<String> = emptyList()
)

data class MediaRow(
    val title: String,
    val items: List<MediaItem>,
    /** pass to MoviesRepository.catalog() for "See all"; null = no see-all */
    val catalogKey: String? = null
)

/** Same title in another audio language (MovieBox ships dubs as separate subjects). */
data class AudioVariant(
    val id: String,
    val label: String,
    val languageCode: String,
    val isCurrent: Boolean
)

data class MediaSeason(val number: Int, val episodeCount: Int)

data class MediaDetail(
    /** canonical id after resolution (a Cinemeta "tt…" id resolves to the matching "mb:…" id) */
    val id: String,
    val type: MediaType,
    val title: String,
    val description: String? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val year: Int? = null,
    val rating: Double? = null,
    val runtime: String? = null,
    val country: String? = null,
    val genres: List<String> = emptyList(),
    val cast: List<String> = emptyList(),
    val seasons: List<MediaSeason> = emptyList(),
    val audioVariants: List<AudioVariant> = emptyList(),
    /** false when no streaming source carries this title (show "Not available to stream") */
    val playable: Boolean = true
)

data class SubtitleTrack(
    val url: String,
    val languageCode: String,
    val label: String,
    /** "application/x-subrip" for .srt, "text/vtt" for .vtt */
    val mimeType: String
)

data class MediaStream(
    val url: String,
    val qualityLabel: String,
    val height: Int = 0,
    val codec: String? = null,
    val sizeBytes: Long? = null,
    /** request headers the CDN needs (User-Agent, Referer, Cookie …) */
    val headers: Map<String, String> = emptyMap(),
    val subtitles: List<SubtitleTrack> = emptyList(),
    /** "MovieBox", or the Stremio addon's name */
    val sourceName: String,
    /** progressive file (mp4/mkv) vs adaptive manifest (m3u8/mpd) */
    val isDirectFile: Boolean = true,
    /** explicit MIME when the URL has no telling extension, e.g. "application/x-mpegURL" */
    val mimeType: String? = null
)

data class Addon(
    val id: String,
    val name: String,
    val manifestUrl: String,
    val description: String? = null,
    val enabled: Boolean = true,
    /** Cinemeta is built in and can't be removed */
    val builtIn: Boolean = false
)

data class Playlist(val id: Long, val name: String, val url: String)

data class Channel(
    val id: String,
    val name: String,
    val url: String,
    val logoUrl: String? = null,
    val group: String? = null,
    val headers: Map<String, String> = emptyMap()
)

/**
 * Everything the generic media player needs. The caller fills it, hands it to
 * [com.anistream.domain.repository.NowPlaying] and navigates to the media player route.
 */
data class PlaybackRequest(
    /** watch-history key, e.g. "mb:123" for a movie or "mb:123:s1e2" for an episode; null for live TV */
    val historyKey: String?,
    val mediaId: String?,
    val title: String,
    val subtitle: String? = null,
    val posterUrl: String? = null,
    val streams: List<MediaStream>,
    val isLive: Boolean = false,
    val season: Int = 0,
    val episode: Int = 0,
    /** total episodes in this season (for next-episode); 0 for movies/live */
    val seasonEpisodeCount: Int = 0
)
