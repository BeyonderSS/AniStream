package com.anistream.core.parser

import com.anistream.core.common.Result
import com.anistream.domain.model.AnimeDetail
import com.anistream.domain.model.AnimeSearchResult
import com.anistream.domain.model.Episode
import com.anistream.domain.model.Genre
import com.anistream.domain.model.ScheduleEntry
import com.anistream.domain.model.Season
import com.anistream.domain.model.StreamSource
import com.anistream.domain.repository.HomeContent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import java.util.Base64

/** Decoded zokoanime embed config: master playlist + chosen subtitle track. */
data class EmbedConfig(val masterUrl: String, val subtitleUrl: String?)

/** Pure hianime.at / zokoanime parsing, no Android or network so it unit-tests on the JVM. */
object HianimeParser {

    private const val EMBED_KEY = "otaku-embed-v1"
    private val BLOB = Regex("""window\.__P\s*=\s*"([^"]+)"""")
    private val DIGITS = Regex("""\d+""")
    private val YEAR = Regex("""\b(19|20)\d{2}\b""")

    /** Last path segment of an anime url: https://hianime.at/watch/foo-481?ep=1 -> foo-481 */
    fun slugFromUrl(url: String): String = url.substringBefore('?').trimEnd('/').substringAfterLast('/')

    /** Numeric id hianime uses in its api: digits after the last '-' of the slug. */
    fun numericId(slug: String): Long? = slug.substringAfterLast('-').toLongOrNull()

    /** Card grids: search, /top-airing, /most-popular, /genres/<slug> ... */
    fun parseCards(html: String): Result<List<AnimeSearchResult>> = parseResult { cards(mainContent(html)) }

    fun parseHome(html: String): Result<HomeContent> = parseResult {
        val doc = mainContent(html)
        val trending = doc.select("#trending-home .swiper-slide .item").mapNotNull { item ->
            val a = item.selectFirst("a.film-poster") ?: return@mapNotNull null
            AnimeSearchResult(
                slug = slugFromUrl(a.attr("href")),
                title = a.attr("title").ifBlank { item.selectFirst(".film-title")?.text().orEmpty() },
                posterUrl = a.selectFirst("img")?.attr("src")?.ifBlank { null }
            )
        }
        fun block(header: String): List<AnimeSearchResult> =
            doc.select("#anime-featured .anif-block")
                .firstOrNull { it.selectFirst(".anif-block-header")?.text().equals(header, true) }
                ?.select("li")?.mapNotNull(::card).orEmpty()
        HomeContent(
            trending = trending,
            topAiring = block("Top Airing"),
            popular = block("Most Popular"),
            favorites = block("Most Favorite"),
            completed = block("Latest Completed"),
            recentEpisodes = section(doc, "Latest Episode")?.let(::cards).orEmpty()
        )
    }

    /** Genre list from the sidebar widget of any page (home works). */
    fun parseGenres(html: String): Result<List<Genre>> = parseResult {
        val doc = Jsoup.parse(html)
        val links = doc.select(".hianime-genres-widget a[href*=/genres/]").ifEmpty { doc.select("a[href*=/genres/]") }
        links.map { slugFromUrl(it.attr("href")) to it.text() }
            .filter { it.first.isNotBlank() && it.second.isNotBlank() }
            .distinctBy { it.first }
            .mapIndexed { i, (slug, name) -> Genre(id = i, name = name, slug = slug) }
    }

    fun parseDetail(html: String, slug: String): Result<AnimeDetail> = parseResult {
        val doc = mainContent(html)
        val nameEl = doc.selectFirst("h2.film-name")
        val stats = doc.selectFirst(".film-stats")
        fun tick(cls: String) = stats?.selectFirst(".tick-$cls")?.text()?.let { DIGITS.find(it)?.value?.toIntOrNull() }
        val statItems = stats?.select(".item")?.map { it.text() }.orEmpty()

        val info = doc.select(".anisc-info .item").associateBy { it.selectFirst(".item-head")?.text()?.trim().orEmpty() }
        fun infoText(label: String) = info[label]?.selectFirst(".name")?.text()?.ifBlank { null }
        fun infoLinks(label: String) = info[label]?.select("a")?.map { it.text() }?.filter { it.isNotBlank() }.orEmpty()
        val premiered = infoText("Premiered:")
        val sub = tick("sub")
        val dub = tick("dub")

        AnimeDetail(
            slug = slug,
            title = nameEl?.text().orEmpty(),
            titleEnglish = nameEl?.text()?.ifBlank { null },
            titleJapanese = nameEl?.attr("data-jname")?.ifBlank { null } ?: infoText("Japanese:"),
            synopsis = doc.selectFirst(".film-description .text")?.text()?.ifBlank { null },
            posterUrl = doc.selectFirst(".anisc-poster img")?.attr("src")?.ifBlank { null },
            type = statItems.getOrNull(0),
            episodes = tick("eps") ?: listOfNotNull(sub, dub).maxOrNull(),
            status = infoText("Status:"),
            score = infoText("MAL Score:")?.toDoubleOrNull(),
            rating = stats?.selectFirst(".tick-pg")?.text()?.ifBlank { null },
            duration = infoText("Duration:") ?: statItems.getOrNull(1),
            year = (premiered ?: infoText("Aired:"))?.let { YEAR.find(it)?.value?.toIntOrNull() },
            season = premiered?.substringBefore(' ')?.ifBlank { null },
            genres = info["Genres:"]?.select("a[href*=/genres/]")?.mapIndexed { i, a ->
                Genre(id = i, name = a.text(), slug = slugFromUrl(a.attr("href")))
            }.orEmpty(),
            studios = infoLinks("Studios:"),
            producers = infoLinks("Producers:"),
            seasons = doc.select(".os-list a.os-item").map { a ->
                Season(slug = slugFromUrl(a.attr("href")), title = a.selectFirst(".title")?.text()?.ifBlank { null } ?: a.attr("title"))
            },
            subEpisodes = sub,
            dubEpisodes = dub,
            recommendations = section(doc, "Recommended For You")?.let(::cards).orEmpty()
        )
    }

    /** /api/theme/episode/list/<id>; drops items that link to a different anime. */
    fun parseEpisodes(json: String, slug: String): Result<List<Episode>> = parseResult {
        Jsoup.parse(jsonHtml(json)).select("a.ep-item")
            .filter { it.attr("href").contains("/watch/$slug?") }
            .mapNotNull { a ->
                val id = a.attr("data-id").toLongOrNull() ?: return@mapNotNull null
                val number = a.attr("data-number").toIntOrNull() ?: return@mapNotNull null
                Episode(id = id, number = number, title = a.attr("title").ifBlank { null })
            }
    }

    /** /api/theme/episode/servers -> zokoanime embed url for "sub"/"dub" (also "jpn"/"eng"). */
    fun parseEmbedUrl(json: String, language: String): Result<String> = parseResult {
        embedUrls(json, language).firstOrNull() ?: throw NoSuchElementException(NOT_AVAILABLE)
    }

    /**
     * Every embed for this language that deobfuscate-able (zokoanime) player serves. Picked by the decoded
     * host, not the label: the site sometimes labels a zokoanime embed "HD-1".
     */
    fun embedUrls(json: String, language: String): List<String> {
        val type = when (language.lowercase()) {
            "jpn", "sub" -> "sub"
            "eng", "dub" -> "dub"
            else -> language.lowercase()
        }
        return Jsoup.parse(jsonHtml(json))
            .select(".server-item[data-type=$type]")
            .sortedByDescending { it.attr("data-server-name").equals("ZokoAnime", true) }
            .mapNotNull { runCatching { String(Base64.getDecoder().decode(it.attr("data-hash").trim()), Charsets.UTF_8) }.getOrNull() }
            .filter { it.contains("zokoanime", ignoreCase = true) }
            .distinct()
    }

    const val NOT_AVAILABLE = "This isn't available to stream yet. Check back after it releases."

    /** Embed page ships its config as base64(json XOR "otaku-embed-v1") in window.__P. */
    fun parseEmbed(html: String): Result<EmbedConfig> = parseResult {
        val blob = BLOB.find(html)?.groupValues?.get(1) ?: throw NoSuchElementException(NOT_AVAILABLE)
        val obj = Json.parseToJsonElement(decodeBlob(blob)).asObjectOrNull() ?: error("Embed config is not an object")
        val subs = obj.fieldAsArrayOrNull("subtitles").orEmpty().mapNotNull { it.asObjectOrNull() }
        val sub = subs.firstOrNull { it.fieldBooleanOrNull("default") == true }
            ?: subs.firstOrNull { it.fieldStringOrNull("lang")?.startsWith("en", true) == true }
            ?: subs.firstOrNull()
        EmbedConfig(
            masterUrl = obj.fieldStringOrNull("src") ?: error("Embed config has no src"),
            subtitleUrl = sub.fieldStringOrNull("src")
        )
    }

    fun decodeBlob(blob: String): String {
        val key = EMBED_KEY.toByteArray()
        val bytes = Base64.getDecoder().decode(blob)
        return String(ByteArray(bytes.size) { i -> (bytes[i].toInt() xor key[i % key.size].toInt()).toByte() }, Charsets.UTF_8)
    }

    /** One StreamSource per variant, relative variant uris resolved against the master url. */
    fun parseMasterPlaylist(
        playlist: String,
        masterUrl: String,
        referer: String? = null,
        subtitleUrl: String? = null
    ): Result<List<StreamSource>> = parseResult {
        val base = URI(masterUrl)
        val lines = playlist.lines().map { it.trim() }
        val sources = mutableListOf<StreamSource>()
        for ((i, line) in lines.withIndex()) {
            if (!line.startsWith("#EXT-X-STREAM-INF:")) continue
            val uri = lines.getOrNull(i + 1)?.takeIf { it.isNotEmpty() && !it.startsWith("#") } ?: continue
            val attrs = line.substringAfter(':')
            val res = Regex("""RESOLUTION=(\d+)x(\d+)""").find(attrs)
            val width = res?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val height = res?.groupValues?.get(2)?.toIntOrNull() ?: 0
            sources += StreamSource(
                qualityLabel = if (height > 0) "${height}p" else "auto",
                m3u8Url = base.resolve(uri).toString(),
                width = width,
                height = height,
                bandwidth = Regex("""(?<!AVERAGE-)BANDWIDTH=(\d+)""").find(attrs)?.groupValues?.get(1)?.toLongOrNull() ?: 0,
                referer = referer,
                subtitleUrl = subtitleUrl
            )
        }
        // not a master playlist: the url itself is the media playlist
        if (sources.isEmpty() && playlist.contains("#EXTINF")) {
            sources += StreamSource("auto", masterUrl, referer = referer, subtitleUrl = subtitleUrl)
        }
        sources.sorted()
    }

    /** /api/theme/schedule/day; poster isn't in the payload. */
    fun parseScheduleDay(json: String, dayOfWeek: String): Result<List<ScheduleEntry>> = parseResult {
        Jsoup.parse(jsonHtml(json)).select("a.tsl-link").map { a ->
            ScheduleEntry(
                animeSlug = slugFromUrl(a.attr("href")),
                animeTitle = a.selectFirst(".film-name")?.text().orEmpty(),
                episodeNumber = a.selectFirst(".btn-play")?.text()?.let { DIGITS.find(it)?.value?.toIntOrNull() },
                time = a.selectFirst(".time")?.text()?.ifBlank { null },
                dayOfWeek = dayOfWeek
            )
        }
    }

    // the top-10 sidebar repeats card markup; drop it so lists don't get duplicates
    private fun mainContent(html: String): Document = Jsoup.parse(html).also { it.select("#main-sidebar").remove() }

    private fun section(root: Element, heading: String): Element? =
        root.select("h2.cat-heading").firstOrNull { it.text().equals(heading, true) }?.closest("section")

    private fun cards(root: Element): List<AnimeSearchResult> =
        root.select("div.flw-item").mapNotNull(::card).distinctBy { it.slug }

    private fun card(el: Element): AnimeSearchResult? {
        val a = el.selectFirst(".film-name a") ?: el.selectFirst("a[href]") ?: return null
        val slug = slugFromUrl(a.attr("href")).ifBlank { return null }
        return AnimeSearchResult(
            slug = slug,
            title = a.attr("title").ifBlank { a.text() }.ifBlank { return null },
            posterUrl = el.selectFirst("img.film-poster-img")?.let { it.attr("data-src").ifBlank { it.attr("src") } }?.ifBlank { null },
            type = el.selectFirst(".fd-infor .fdi-item")?.text()?.ifBlank { null }
        )
    }

    private fun jsonHtml(json: String): String =
        (Json.parseToJsonElement(json) as? JsonObject).fieldStringOrNull("html") ?: error("No html in response")
}
