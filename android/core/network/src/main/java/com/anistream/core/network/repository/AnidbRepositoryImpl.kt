package com.anistream.core.network.repository

import com.anistream.core.common.Result
import com.anistream.core.network.AnidbHttpClient
import com.anistream.core.network.AnidbHttpClient.Companion.BASE_URL
import com.anistream.core.parser.HianimeParser
import com.anistream.domain.KidsBlockedException
import com.anistream.domain.KidsPolicy
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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap
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
        val r = HianimeParser.parseDetail(httpClient.getHtml("$BASE_URL/$slug"), slug)
        val d = (r as? Result.Success)?.data
        when {
            !KidsPolicy.active || d == null -> r
            !KidsPolicy.anime(d.rating, d.genres.map { it.name }) -> Result.error(KidsBlockedException())
            else -> Result.success(d.copy(recommendations = d.recommendations.filter(KidsPolicy::animeCard)))
        }
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
        // embed url is ".../stream/mal/<malId>/<ep>/<lang>"; AniSkip keys on that MAL id
        val malId = MAL_ID.find(embedUrl)?.groupValues?.get(1)?.toLongOrNull()
        HianimeParser.parseMasterPlaylist(master, embed.masterUrl, referer, embed.subtitleUrl).map { list -> list.map { it.copy(malId = malId) } }
    }

    override suspend fun getHomeContent(): Result<HomeContent> = fetch {
        HianimeParser.parseHome(httpClient.getHtml("$BASE_URL/home")).map { h ->
            if (!KidsPolicy.active) h else with(KidsPolicy) {
                h.copy(
                    trending = h.trending.filter(::animeCard), topAiring = h.topAiring.filter(::animeCard),
                    popular = h.popular.filter(::animeCard), favorites = h.favorites.filter(::animeCard),
                    completed = h.completed.filter(::animeCard), recentEpisodes = h.recentEpisodes.filter(::animeCard)
                )
            }
        }
    }

    override suspend fun browseCategory(path: String, page: Int): Result<List<AnimeSearchResult>> = fetch {
        if (KidsPolicy.active && path.startsWith("genres/") && KidsPolicy.animeGenreBlocked(path.substringAfter('/').replace('-', ' '))) {
            return Result.error(KidsBlockedException())
        }
        HianimeParser.parseCards(httpClient.getHtml("$BASE_URL/${path.trim('/')}?page=$page"))
    }

    // ponytail: refetches /home each call; cache if the genre screen reloads often
    override suspend fun getGenres(): Result<List<Genre>> = fetch {
        HianimeParser.parseGenres(httpClient.getHtml("$BASE_URL/home")).map { list ->
            if (KidsPolicy.active) list.filterNot { KidsPolicy.animeGenreBlocked(it.name) } else list
        }
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
        val all = days.flatMap { (it as Result.Success).data }
        Result.success(
            if (!KidsPolicy.active) all
            else coroutineScope { all.map { e -> async { e to allowedForKids(e.animeSlug) } }.awaitAll() }.filter { it.second }.map { it.first }
        )
    }

    // ponytail: schedule rows carry no rating, so a kids profile checks each show's detail page once (6 at a time,
    // remembered for the process). Unreachable detail = hidden. Swap for a cached rating index if the first load drags.
    private val kidsOk = ConcurrentHashMap<String, Boolean>()
    private val kidsGate = Semaphore(6)

    private suspend fun allowedForKids(slug: String): Boolean = kidsOk[slug] ?: kidsGate.withPermit {
        kidsOk.getOrPut(slug) { getAnimeDetail(slug).isSuccess() }
    }

    private companion object {
        val MAL_ID = Regex("""/mal/(\d+)/""")
    }

    private fun Result<*>.asError(): Result<Nothing> =
        this as? Result.Error ?: Result.error(IllegalStateException("Unexpected result $this"))
}
