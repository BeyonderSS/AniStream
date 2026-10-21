package com.anistream.feature.party

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** A GitHub release newer than the running app, with the APK for this flavor. */
data class Release(val versionCode: Int, val name: String, val body: String, val apkUrl: String, val sizeBytes: Long)

private val TAG = Regex("""v?1\.0\.(\d+)""")

/** "v1.0.7" -> 107 (CI ships versionCode 100 + N); anything else -> null. */
fun tagToVersionCode(tag: String): Int? =
    TAG.matchEntire(tag.trim())?.groupValues?.get(1)?.toIntOrNull()?.plus(100)

/** Phone assets are "Reelm-v*.apk", TV assets "Reelm-TV-v*.apk" (the two prefixes never overlap). */
fun pickAsset(assets: List<Pair<String, String>>, tv: Boolean): String? {
    val prefix = if (tv) "Reelm-TV-v" else "Reelm-v"
    return assets.firstOrNull { (name, _) -> name.startsWith(prefix) && name.endsWith(".apk") }?.second
}

/** Debug builds and local builds (versionCode below 100, CI always ships 101+) never get offered an update. */
fun shouldOffer(current: Int, latest: Int, debuggable: Boolean) = !debuggable && current >= 100 && latest > current

fun parseRelease(json: JsonObject, tv: Boolean): Release? {
    val code = tagToVersionCode(json["tag_name"]?.jsonPrimitive?.contentOrNull ?: return null) ?: return null
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
