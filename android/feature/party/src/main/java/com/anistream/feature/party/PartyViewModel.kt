package com.anistream.feature.party

import androidx.lifecycle.ViewModel
import com.anistream.core.account.AccountRepository
import com.anistream.core.common.Result
import com.anistream.domain.model.PlaybackRequest
import com.anistream.domain.repository.MoviesRepository
import com.anistream.domain.repository.NowPlaying
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import javax.inject.Inject

/** Where joining a party sends the user. */
sealed interface PartyDestination {
    /** [NowPlaying] is already filled; open the media player */
    data object Media : PartyDestination
    data class Anime(val slug: String, val episode: Int) : PartyDestination
}

fun animePayload(slug: String, title: String, episode: Int, posterUrl: String?): JsonObject = buildJsonObject {
    put("kind", "anime"); put("slug", slug); put("title", title); put("episode", episode); put("posterUrl", posterUrl)
}

fun mediaPayload(r: PlaybackRequest): JsonObject = buildJsonObject {
    put("kind", "media"); put("historyKey", r.historyKey); put("mediaId", r.mediaId); put("title", r.title)
    put("subtitle", r.subtitle); put("posterUrl", r.posterUrl); put("season", r.season); put("episode", r.episode)
    put("seasonEpisodeCount", r.seasonEpisodeCount)
}

@HiltViewModel
class PartyViewModel @Inject constructor(
    val client: PartyClient,
    private val movies: MoviesRepository,
    private val accounts: AccountRepository
) : ViewModel() {
    private val profile get() = accounts.session.value?.activeProfile
    private val name get() = profile?.name?.ifBlank { null } ?: GUEST
    private val avatar get() = profile?.avatar.orEmpty()

    /** Other members switching media: the player destination to replace the current one with. Collect from the nav host. */
    val loads: Flow<PartyDestination> = client.loads.map { resolve(it) }.filterNotNull().onEach { client.switching = true }

    /** Creates a room for [payload] and joins it as host (as the active profile, "Guest" if none); returns the code or null. */
    suspend fun create(payload: JsonObject): String? =
        client.create(payload)?.also { client.join(it, name, avatar, host = true) }

    /** Joins [code], waits for the room `state`, and prepares the player for its payload. Null = couldn't join. */
    suspend fun join(code: String): PartyDestination? {
        client.join(code, name, avatar)
        val room = withTimeoutOrNull(8_000) { client.room.filterNotNull().first() }
        val payload = room?.payload
        val dest = payload?.let { resolve(it) }
        if (dest == null) client.leave()
        return dest
    }

    private suspend fun resolve(p: JsonObject): PartyDestination? {
        fun s(k: String) = p[k]?.jsonPrimitive?.contentOrNull
        fun i(k: String) = p[k]?.jsonPrimitive?.intOrNull ?: 0
        return when (s("kind")) {
            "anime" -> PartyDestination.Anime(s("slug") ?: return null, i("episode"))
            "media" -> {
                val id = s("mediaId") ?: return null
                val streams = (movies.streams(id, i("season"), i("episode")) as? Result.Success)?.data?.takeIf { it.isNotEmpty() } ?: return null
                NowPlaying.request = PlaybackRequest(
                    historyKey = s("historyKey"), mediaId = id, title = s("title").orEmpty(), subtitle = s("subtitle"),
                    posterUrl = s("posterUrl"), streams = streams, season = i("season"), episode = i("episode"),
                    seasonEpisodeCount = i("seasonEpisodeCount")
                )
                PartyDestination.Media
            }
            else -> null
        }
    }
}
