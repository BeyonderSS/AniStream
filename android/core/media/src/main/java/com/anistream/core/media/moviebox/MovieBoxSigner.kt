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
    private val KEY = intArrayOf(
        0xef, 0xa8, 0x91, 0x97, 0x4e, 0xec, 0xd3, 0x14, 0x8d, 0xf6, 0x3a, 0xa6, 0x11, 0x60, 0x2d,
        0xef, 0xd1, 0x01, 0x25, 0x9b, 0xa5, 0x21, 0x02, 0x2c, 0x57, 0xae, 0x05, 0x66, 0xbd, 0x8e
    ).map { it.toByte() }.toByteArray()

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
