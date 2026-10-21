package com.anistream.feature.party

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** A GitHub release newer than the running app, with the APK for this flavor. */
data class Release(val versionCode: Int, val name: String, val body: String, val apkUrl: String, val sizeBytes: Long)

private val TAG = Regex("""v?1\.0\.(\d+)(?:-([A-Za-z0-9-]+))?""")
private val VERSION = Regex("""1\.0\.\d+(?:-(.+))?""")

/** "v1.0.7" -> 107 (CI ships versionCode 100 + N); a feature-branch tag like "v1.0.7-my-branch" or anything else -> null. */
fun tagToVersionCode(tag: String): Int? = tagCode(tag, channel = null)

/**
 * Like [tagToVersionCode], but a tag carrying a branch suffix counts when it is [channel]'s own branch.
 * Stable builds (channel null) therefore never see a feature build, and a feature build never sees another branch's.
 */
fun tagCode(tag: String, channel: String?): Int? {
    val m = TAG.matchEntire(tag.trim()) ?: return null
    val slug = m.groupValues[2]
    if (slug.isNotEmpty() && slug != channel) return null
    return m.groupValues[1].toIntOrNull()?.plus(100)
}

/** "1.0.22" / "1.0.22-tv" -> null (stable); "1.0.22-my-branch" / "1.0.22-my-branch-tv" -> "my-branch" (CI feature build). */
fun channelOf(versionName: String): String? =
    VERSION.matchEntire(versionName.removeSuffix("-tv"))?.groupValues?.get(1)?.takeIf { it.isNotEmpty() }

/** Phone assets are "Reelm-v*.apk", TV assets "Reelm-TV-v*.apk" (the two prefixes never overlap). */
fun pickAsset(assets: List<Pair<String, String>>, tv: Boolean): String? {
    val prefix = if (tv) "Reelm-TV-v" else "Reelm-v"
    return assets.firstOrNull { (name, _) -> name.startsWith(prefix) && name.endsWith(".apk") }?.second
}

/** Debug builds and local builds (versionCode below 100, CI always ships 101+) never get offered an update. */
fun shouldOffer(current: Int, latest: Int, debuggable: Boolean) = !debuggable && current >= 100 && latest > current

/** [channel] null = stable build: pre-releases and drafts are never offered. A feature build also gets pre-releases of its own branch. */
fun parseRelease(json: JsonObject, tv: Boolean, channel: String? = null): Release? {
    if (json["draft"]?.jsonPrimitive?.booleanOrNull == true) return null
    if (channel == null && json["prerelease"]?.jsonPrimitive?.booleanOrNull == true) return null
    val code = tagCode(json["tag_name"]?.jsonPrimitive?.contentOrNull ?: return null, channel) ?: return null
    val assets = (json["assets"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
    val asset = assets.firstOrNull { a ->
        pickAsset(listOf(a["name"]?.jsonPrimitive?.contentOrNull.orEmpty() to "x"), tv) != null
    } ?: return null
    return Release(
        versionCode = code,
        name = json["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: "Version ${code - 100}",
        body = json["body"]?.jsonPrimitive?.contentOrNull.orEmpty().trim(),
        apkUrl = asset["browser_download_url"]?.jsonPrimitive?.contentOrNull ?: return null,
        sizeBytes = asset["size"]?.jsonPrimitive?.longOrNull ?: 0L
    )
}

/** Newest offerable release in a /releases listing: the stable ones plus [channel]'s own pre-releases. */
fun pickNewest(releases: JsonArray, tv: Boolean, channel: String?): Release? =
    releases.mapNotNull { (it as? JsonObject)?.let { r -> parseRelease(r, tv, channel) } }.maxByOrNull { it.versionCode }
