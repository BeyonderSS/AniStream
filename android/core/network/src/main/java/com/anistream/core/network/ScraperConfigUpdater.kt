package com.anistream.core.network

import android.content.Context
import android.util.Base64
import android.util.Log
import com.anistream.core.common.ScraperConfig
import com.anistream.core.common.ScraperSignature
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hot-updates scraper constants without an app release. scrapers.json is published by .github/workflows/scraper-sync.yml
 * together with a detached ECDSA signature. A file is applied only when that signature verifies against the public key
 * below; anything else (offline, unsigned, tampered, malformed) leaves the values the app shipped with.
 */
@Singleton
class ScraperConfigUpdater @Inject constructor(@ApplicationContext private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val http = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()
    private val cache = File(context.filesDir, "scrapers.json")
    private val cacheSig = File(context.filesDir, "scrapers.json.sig")

    /** Applies the last verified copy right away, then refreshes from GitHub in the background. */
    fun start() {
        scope.launch {
            runCatching { if (cache.exists() && cacheSig.exists()) accept("cache", cache.readBytes(), cacheSig.readText()) }
                .onFailure { Log.w(TAG, "cached config unreadable: ${it.message}") }
            runCatching {
                val body = fetch(URL)
                val sig = fetch("$URL.sig")?.decodeToString()
                if (body == null || sig == null) { Log.i(TAG, "no remote config (keeping ${summary()})"); return@launch }
                if (accept("remote", body, sig)) { cache.writeBytes(body); cacheSig.writeText(sig) }
            }.onFailure { Log.w(TAG, "remote config check failed: ${it.message}") }
        }
    }

    private fun fetch(url: String): ByteArray? =
        http.newCall(Request.Builder().url(url).build()).execute().use { if (it.isSuccessful) it.body?.bytes() else null }

    private fun accept(origin: String, body: ByteArray, sig: String): Boolean {
        val ok = ScraperSignature.verify(body, Base64.decode(sig.trim(), Base64.DEFAULT), Base64.decode(PUBLIC_KEY, Base64.DEFAULT))
        if (ok) { applyConfig(Json.parseToJsonElement(body.decodeToString()).jsonObject); Log.i(TAG, "applied $origin config: ${summary()}") }
        else Log.w(TAG, "rejected $origin config: bad signature (keeping ${summary()})")
        return ok
    }

    /** Non-secret values only, so a log shows which config is live. */
    private fun summary() = "hianime=${ScraperConfig.hianimeBase} dramachi=${ScraperConfig.dramachiBase} 4khdhub=${ScraperConfig.fourKHdHubBase} " +
        "moviebox=${ScraperConfig.movieBoxHosts.size} hosts v${ScraperConfig.movieBoxVersionName}"

    companion object {
        private const val TAG = "ScraperConfig"
        private const val URL = "https://raw.githubusercontent.com/BeyonderSS/Reelm/main/scrapers/scrapers.json"
        private const val PUBLIC_KEY = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEw0hwDabVPIDxew9Agk+tBkl1eSu9an4/527zHo7NI7Nj0x/RygIf6cqh0VSTNLvVjuQCy3qQ9KZu3T34IxycWw=="

        private fun JsonObject.cfg(source: String): JsonObject? = (this[source] as? JsonObject)?.get("config") as? JsonObject
        private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.takeIf { it.isNotBlank() }
        private fun JsonObject.strs(k: String): List<String>? = (this[k] as? JsonArray)?.map { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content ?: return null }?.takeIf { it.isNotEmpty() }
        private fun JsonObject.ints(k: String): List<Int>? = (this[k] as? JsonArray)?.map { (it as? JsonPrimitive)?.intOrNull ?: return null }?.takeIf { it.isNotEmpty() }
        private fun JsonObject.pairs(k: String): List<Pair<String, String>>? = (this[k] as? JsonArray)?.map { row ->
            val r = row as? JsonArray ?: return null
            (r.getOrNull(0) as? JsonPrimitive)?.contentOrNull.orEmpty() to (r.getOrNull(1) as? JsonPrimitive)?.contentOrNull.orEmpty()
        }?.takeIf { p -> p.isNotEmpty() && p.all { it.first.isNotEmpty() } }

        /** Field by field and only when it has the right shape, so a bad value never replaces a good default. */
        internal fun applyConfig(doc: JsonObject) {
            doc.cfg("hianime")?.let { c ->
                c.str("base")?.let { ScraperConfig.hianimeBase = it.trimEnd('/') }
                c.str("key")?.let { ScraperConfig.embedKey = it }
                c.str("server")?.let { ScraperConfig.hianimeServer = it }
            }
            doc.cfg("moviebox")?.let { c ->
                c.ints("secret")?.takeIf { s -> s.all { it in 0..255 } }?.let { s -> ScraperConfig.movieBoxSecret = s.map { it.toByte() }.toByteArray() }
                c.strs("hosts")?.takeIf { h -> h.all { it.startsWith("https://") } }?.let { ScraperConfig.movieBoxHosts = it }
                c.ints("retryStatus")?.let { ScraperConfig.movieBoxRetry = it.toSet() }
                c.ints("versionCodes")?.let { ScraperConfig.movieBoxVersionCodes = it }
                c.pairs("androidVersions")?.let { ScraperConfig.movieBoxAndroid = it }
                c.pairs("devices")?.let { d -> ScraperConfig.movieBoxModels = d.map { it.first } }
                c.str("packageName")?.let { ScraperConfig.movieBoxPackage = it }
                c.str("versionName")?.let { ScraperConfig.movieBoxVersionName = it }
                c.str("spCode")?.let { ScraperConfig.movieBoxSpCode = it }
                c.str("cronet")?.let { ScraperConfig.movieBoxCronet = it }
            }
            doc.cfg("dramachi")?.let { c ->
                c.str("base")?.takeIf { it.startsWith("https://") }?.let { ScraperConfig.dramachiBase = if (it.endsWith("/")) it else "$it/" }
                c.str("imageCdn")?.takeIf { it.startsWith("https://") }?.let { ScraperConfig.dramachiImageCdn = it }
            }
            doc.cfg("fourkhdhub")?.let { c ->
                c.str("base")?.takeIf { it.startsWith("https://") }?.let { ScraperConfig.fourKHdHubBase = it.trimEnd('/') }
                c.strs("mirrors")?.let { ScraperConfig.fourKHdHubMirrors = it }
            }
        }
    }
}
