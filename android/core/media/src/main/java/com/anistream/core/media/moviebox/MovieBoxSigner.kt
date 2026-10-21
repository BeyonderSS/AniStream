package com.anistream.core.media.moviebox

import java.net.URI
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Request signing for the MovieBox (oneroom) mobile API. Port of MovieBox-TUI's crypto.rs (Apache-2.0).
 * Pure JVM so it is unit-testable.
 */
object MovieBoxSigner {
    // the shipped key lives in ScraperConfig, which scrapers.json can rotate without an app release
    private val KEY: ByteArray get() = com.anistream.core.common.ScraperConfig.movieBoxSecret

    private const val BODY_HASH_LIMIT = 102_400
    const val JSON = "application/json"

    fun md5Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }

    /** "<ts>,<md5(reversed ts)>" */
    fun clientToken(ts: Long): String = "$ts,${md5Hex(ts.toString().reversed().toByteArray())}"

    /** path + "?" + query sorted by key (stable for repeated keys), values decoded, as the server canonicalises. */
    fun canonicalUrl(url: String): String {
        val uri = URI(url)
        val pairs = uri.rawQuery.orEmpty().split('&').filter { it.isNotEmpty() }.map {
            val k = it.substringBefore('=')
            val v = it.substringAfter('=', "")
            URLDecoder.decode(k, "UTF-8") to URLDecoder.decode(v, "UTF-8")
        }.sortedBy { it.first }
        val path = uri.rawPath.orEmpty()
        return if (pairs.isEmpty()) path else path + "?" + pairs.joinToString("&") { "${it.first}=${it.second}" }
    }

    fun canonicalString(method: String, url: String, body: String?, ts: Long): String {
        val bytes = body?.toByteArray()
        val bodyHash = bytes?.let { md5Hex(if (it.size > BODY_HASH_LIMIT) it.copyOf(BODY_HASH_LIMIT) else it) }.orEmpty()
        val bodyLength = bytes?.size?.toString().orEmpty()
        return listOf(method.uppercase(), JSON, JSON, bodyLength, ts.toString(), bodyHash, canonicalUrl(url)).joinToString("\n")
    }

    /** "<ts>|2|<base64(HMAC-MD5)>" */
    fun signature(method: String, url: String, body: String?, ts: Long): String {
        val mac = Mac.getInstance("HmacMD5").apply { init(SecretKeySpec(KEY, "HmacMD5")) }
        val sig = Base64.getEncoder().encodeToString(mac.doFinal(canonicalString(method, url, body, ts).toByteArray()))
        return "$ts|2|$sig"
    }
}
