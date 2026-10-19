package com.anistream.core.network.repository

import com.anistream.core.common.Result
import com.anistream.core.network.AnidbHttpClient
import com.anistream.core.network.AnidbHttpClient.Companion.BASE_URL
import com.anistream.core.parser.HianimeParser
import com.anistream.domain.model.AnimeDetail
import com.anistream.domain.model.AnimeSearchResult
import com.anistream.domain.model.Episode
import com.anistream.domain.model.Genre
import com.anistream.domain.model.ScheduleEntry
import com.anistream.domain.model.StreamSource
import com.anistream.domain.repository.AnidbRepository
import com.anistream.domain.repository.HomeContent
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.net.URI
import java.net.URLEncoder
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** hianime.at scraper; parsing lives in core:parser's HianimeParser. */
@Singleton
class AnidbRepositoryImpl @Inject constructor(
    private val httpClient: AnidbHttpClient
) : AnidbRepository {

    // network errors become Result.Error like the parsers' failures
    private inline fun <T> fetch(block: () -> Result<T>): Result<T> =
        try { block() } catch (e: kotlin.coroutines.cancellation.CancellationException) { throw e } catch (e: Exception) { android.util.Log.w("AnidbRepository", "request failed", e); Result.error(e) }

    override suspend fun searchAnime(query: String, page: Int): Result<List<AnimeSearchResult>> = fetch {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        HianimeParser.parseCards(httpClient.getHtml("$BASE_URL/search?keyword=$q&page=$page"))
    }

    override suspend fun getAnimeDetail(slug: String): Result<AnimeDetail> = fetch {
        HianimeParser.parseDetail(httpClient.getHtml("$BASE_URL/$slug"), slug)
    }

    override suspend fun getEpisodes(slug: String): Result<List<Episode>> = fetch {
        val id = HianimeParser.numericId(slug) ?: return Result.error(IllegalArgumentException("Invalid slug: $slug"))
        HianimeParser.parseEpisodes(httpClient.getHtml("$BASE_URL/api/theme/episode/list/$id"), slug)
    }

    override suspend fun resolveStream(episodeId: Long, language: String): Result<List<StreamSource>> = fetch {
        val servers = httpClient.getHtml("$BASE_URL/api/theme/episode/servers?episodeId=$episodeId")
        // try each playable embed: a listed server can 404 ("Not found") when the video isn't uploaded yet
        val urls = HianimeParser.embedUrls(servers, language)
        var embedUrl = ""
        var embed: com.anistream.core.parser.EmbedConfig? = null
        for (url in urls) {
            val r = HianimeParser.parseEmbed(httpClient.getHtml(url))
            if (r is Result.Success) { embedUrl = url; embed = r.data; break }
        }
        if (embed == null) return Result.error(NoSuchElementException(HianimeParser.NOT_AVAILABLE))
        // stream host 403s without the embed site as Referer, e.g. https://zokoanime.video/
        val referer = URI(embedUrl).resolve("/").toString()
        val master = httpClient.getHtml(embed.masterUrl, mapOf("Referer" to referer))
        HianimeParser.parseMasterPlaylist(master, embed.masterUrl, referer, embed.subtitleUrl)
    }

    override suspend fun getHomeContent(): Result<HomeContent> = fetch {
        HianimeParser.parseHome(httpClient.getHtml("$BASE_URL/home"))
    }

    override suspend fun browseCategory(path: String, page: Int): Result<List<AnimeSearchResult>> = fetch {
        HianimeParser.parseCards(httpClient.getHtml("$BASE_URL/${path.trim('/')}?page=$page"))
    }

    // ponytail: refetches /home each call; cache if the genre screen reloads often
    override suspend fun getGenres(): Result<List<Genre>> = fetch {
        HianimeParser.parseGenres(httpClient.getHtml("$BASE_URL/home"))
    }

    override suspend fun getSchedule(): Result<List<ScheduleEntry>> = fetch {
        // same sign as JS Date.getTimezoneOffset(): UTC+5:30 -> -330
        val tzOffset = -ZonedDateTime.now().offset.totalSeconds / 60
        val today = LocalDate.now()
        val days = coroutineScope {
            (0L..6L).map { today.plusDays(it) }.map { date ->
                async {
                    val json = httpClient.getHtml("$BASE_URL/api/theme/schedule/day?tzOffset=$tzOffset&date=$date")
                    HianimeParser.parseScheduleDay(json, date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH))
                }
            }.awaitAll()
        }
        days.firstOrNull { it !is Result.Success }?.let { return it.asError() }
        Result.success(days.flatMap { (it as Result.Success).data })
    }

    private fun Result<*>.asError(): Result<Nothing> =
        this as? Result.Error ?: Result.error(IllegalStateException("Unexpected result $this"))
}
