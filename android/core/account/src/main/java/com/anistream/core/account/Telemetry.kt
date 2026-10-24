package com.anistream.core.account

import android.content.Context
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

/**
 * Playback/crash/update events for the admin dashboard (POST /v1/telemetry). Queued in memory and flushed
 * every 60 s by [AccountRuntime], signed-in only. A crash trace is written to disk and sent on the next start.
 */
object Telemetry {
    private val queue = ArrayList<JsonObject>()

    fun track(kind: String, vararg detail: Pair<String, Any?>) = synchronized(queue) {
        if (queue.size >= 40) return@synchronized // offline for a while: drop, it's only telemetry
        queue += buildJsonObject {
            put("kind", kind)
            put("detail", JsonObject(detail.filter { it.second != null }.associate { (k, v) ->
                k to if (v is Number) JsonPrimitive(v) else JsonPrimitive(v.toString().take(1500))
            }))
        }
    }

    fun installCrashHandler(context: Context) {
        val file = File(context.filesDir, "last_crash.txt")
        if (file.exists()) {
            track("crash", "trace" to file.readText().take(3500))
            file.delete()
        }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { file.writeText("${t.name}: ${e.stackTraceToString()}") }
            previous?.uncaughtException(t, e)
        }
    }

    internal suspend fun flush(api: AccountApi, token: String, profileId: String?, platform: String, version: String) {
        val batch = synchronized(queue) { queue.take(20).also { queue.subList(0, it.size).clear() } }
        if (batch.isEmpty()) return
        try {
            api.call("POST", "/v1/telemetry", buildJsonObject {
                put("events", buildJsonArray { batch.forEach { add(it) } })
                profileId?.let { put("profileId", it) }
                put("platform", platform)
                put("appVersion", version)
            }, token)
        } catch (e: ApiException) {
            if (e.code == 0 || e.code == 429 || e.code >= 500) synchronized(queue) { queue.addAll(0, batch) } // retry next tick
        }
    }
}
