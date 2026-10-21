package com.anistream.feature.party

import com.anistream.core.media.MediaHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class Member(val id: String, val name: String, val avatar: String)

/** A room we are in: [payload] = what to play, [last] = the latest play/pause/seek (or the join `state`). */
data class Room(val code: String, val members: List<Member>, val payload: JsonObject?, val host: Boolean, val last: PartyEvent?)

/** [type] "play" | "pause" | "seek"; [position] ms; [at] server ms. */
data class PartyEvent(val type: String, val position: Long, val at: Long, val by: String)

/** Watch-together socket against the server's Room Durable Object (see server/API.md). One room at a time. */
@Singleton
class PartyClient @Inject constructor(private val http: MediaHttp) {
    val enabled: Boolean get() = BuildConfig.REELM_API_URL.isNotBlank()

    private val _room = MutableStateFlow<Room?>(null)
    val room: StateFlow<Room?> = _room.asStateFlow()
    private val _events = MutableSharedFlow<PartyEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<PartyEvent> = _events.asSharedFlow()

    /** payloads other members switched the room to (own echoes are dropped) */
    private val _loads = MutableSharedFlow<JsonObject>(extraBufferCapacity = 8)
    val loads: SharedFlow<JsonObject> = _loads.asSharedFlow()
    /** set while the player is being replaced for a `load`, so the old screen's dispose doesn't leave the room */
    @Volatile var switching = false

    private var socket: WebSocket? = null
    /** our member id if the server tells us ("you" in `state`); the contract doesn't, so echoes also fall to the drift check */
    @Volatile var selfId: String? = null
        private set
    // server clock minus ours; the largest sample is the one with the least network delay
    // ponytail: no re-sync if the server clock steps; reconnect to reset
    @Volatile private var offset: Long? = null

    fun serverNow(): Long = System.currentTimeMillis() + (offset ?: 0L)

    /** POST /v1/rooms; returns the 6-char code, or null on any failure. */
    suspend fun create(payload: JsonObject): String? = withContext(Dispatchers.IO) {
        if (!enabled) return@withContext null
        try {
            val body = buildJsonObject { put("payload", payload) }.toString().toRequestBody("application/json".toMediaType())
            http.client.newCall(Request.Builder().url("${BuildConfig.REELM_API_URL}/v1/rooms").post(body).build()).execute().use { r ->
                if (!r.isSuccessful) return@withContext null
                MediaHttp.json.parseToJsonElement(r.body?.string().orEmpty()).jsonObject["code"]?.jsonPrimitive?.contentOrNull
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Opens the socket; [room] turns non-null when the server's `state` arrives. */
    fun join(code: String, name: String, avatar: String = "", host: Boolean = false) {
        leave()
        if (!enabled) return
        val client = http.client.newBuilder().readTimeout(0, TimeUnit.MILLISECONDS).pingInterval(20, TimeUnit.SECONDS).build()
        val request = Request.Builder().url("${BuildConfig.REELM_API_URL}/v1/rooms/${code.uppercase()}").build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(buildJsonObject { put("type", "join"); put("name", name); put("avatar", avatar) }.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (webSocket === socket) handle(code.uppercase(), host, text)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = closed(webSocket)
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = closed(webSocket)
        })
    }

    fun send(type: String, position: Long) {
        socket?.send(buildJsonObject { put("type", type); put("position", position) }.toString())
    }

    /** We switched media: tell the room. The server's echo is ignored; our own copy of the room is updated now. */
    fun load(payload: JsonObject) {
        socket?.send(buildJsonObject { put("type", "load"); put("payload", payload); put("position", 0) }.toString())
        _room.value = _room.value?.copy(payload = payload)
    }

    fun leave() {
        val s = socket
        socket = null
        _room.value = null
        selfId = null
        offset = null
        s?.close(1000, null)
    }

    private fun closed(ws: WebSocket) {
        if (ws === socket) {
            socket = null
            _room.value = null
        }
    }

    private fun handle(code: String, host: Boolean, text: String) {
        val msg = try { MediaHttp.json.parseToJsonElement(text).jsonObject } catch (_: Exception) { return }
        val type = msg["type"]?.jsonPrimitive?.contentOrNull ?: return
        val at = msg["at"]?.jsonPrimitive?.longOrNull
        if (at != null) offset = maxOf(offset ?: Long.MIN_VALUE, at - System.currentTimeMillis())
        when (type) {
            "state" -> {
                selfId = msg["you"]?.jsonPrimitive?.contentOrNull ?: selfId
                val playing = msg["playing"]?.jsonPrimitive?.contentOrNull == "true"
                val last = PartyEvent(if (playing) "play" else "pause", msg["position"]?.jsonPrimitive?.longOrNull ?: 0L, at ?: serverNow(), "")
                _room.value = Room(code, members(msg["members"]), msg["payload"] as? JsonObject, host, last)
            }
            "load" -> {
                val payload = msg["payload"] as? JsonObject ?: return
                val by = msg["by"]?.jsonPrimitive?.contentOrNull.orEmpty()
                if (isEcho(by, selfId)) return
                // the server reset playback; a non-host member must not push its own state into the new media
                _room.value = _room.value?.copy(payload = payload, host = false, last = PartyEvent("pause", 0L, at ?: serverNow(), by))
                _loads.tryEmit(payload)
            }
            "members" -> _room.value = _room.value?.copy(members = members(msg["members"]))
            "play", "pause", "seek" -> {
                val e = PartyEvent(type, msg["position"]?.jsonPrimitive?.longOrNull ?: return, at ?: return, msg["by"]?.jsonPrimitive?.contentOrNull.orEmpty())
                _room.value = _room.value?.copy(last = if (type == "seek") _room.value?.last?.copy(position = e.position, at = e.at) else e)
                _events.tryEmit(e)
            }
        }
    }

    private fun members(el: kotlinx.serialization.json.JsonElement?): List<Member> =
        (el as? JsonArray).orEmpty().mapNotNull { m ->
            val o = m as? JsonObject ?: return@mapNotNull null
            fun s(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull.orEmpty()
            Member(s("id"), s("name").ifBlank { "Guest" }, s("avatar"))
        }
}
