package com.anistream.core.common

import java.io.ByteArrayOutputStream
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * Checks scrapers.json against its detached signature: ECDSA P-256 / SHA-256, signature = raw r||s (64 bytes) as written
 * by scraper-sync/sign.mjs, public key = X.509 SubjectPublicKeyInfo DER. java.security wants the signature as DER, so convert.
 */
object ScraperSignature {
    fun verify(data: ByteArray, signature: ByteArray, publicKeyDer: ByteArray): Boolean = try {
        if (signature.size != 64) false
        else {
            val key = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(publicKeyDer))
            Signature.getInstance("SHA256withECDSA").run {
                initVerify(key)
                update(data)
                verify(p1363ToDer(signature))
            }
        }
    } catch (_: Exception) {
        false
    }

    internal fun p1363ToDer(sig: ByteArray): ByteArray {
        fun int(bytes: ByteArray): ByteArray {
            val trimmed = bytes.dropWhile { it == 0.toByte() }.toByteArray().ifEmpty { byteArrayOf(0) }
            return if (trimmed[0] < 0) byteArrayOf(0) + trimmed else trimmed // keep it a positive INTEGER
        }
        val r = int(sig.copyOfRange(0, 32))
        val s = int(sig.copyOfRange(32, 64))
        val body = ByteArrayOutputStream().apply {
            write(0x02); write(r.size); write(r)
            write(0x02); write(s.size); write(s)
        }.toByteArray()
        return byteArrayOf(0x30, body.size.toByte()) + body // body is at most 70 bytes, so one length byte is enough
    }
}
