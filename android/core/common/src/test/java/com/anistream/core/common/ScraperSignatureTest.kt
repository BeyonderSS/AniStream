package com.anistream.core.common

import java.util.Base64
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScraperSignatureTest {
    // produced by scraper-sync/sign.mjs's algorithm (node crypto, ECDSA P-256, ieee-p1363)
    private val data = "{\"hianime\":{\"config\":{\"key\":\"x\"}}}\n".toByteArray()
    private val sig = Base64.getDecoder().decode("8YjFWZ4XG4uoeX61RxsMJs77wiBUOyP0zIpS3nZEIp5I399Sbsf7sGF8Y+XOJkgj1bNPn8TNep6WKnM2HNrp0g==")
    private val pub = Base64.getDecoder().decode("MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEPtaveFCtMPMSlJxjdf6lFkSmKXFyzqy3VcsDS2wLyKhMl1X5aEPQSd1zsDEDGo6SZn+/6DihKHvXv1iH5XHLMA==")

    @Test fun acceptsNodeSignature() = assertTrue(ScraperSignature.verify(data, sig, pub))

    @Test fun rejectsTamperedData() = assertFalse(ScraperSignature.verify(data + byteArrayOf(1), sig, pub))

    @Test fun rejectsBadSignatureOrKey() {
        assertFalse(ScraperSignature.verify(data, sig.copyOf(63), pub))
        assertFalse(ScraperSignature.verify(data, ByteArray(64), pub))
        assertFalse(ScraperSignature.verify(data, sig, pub.copyOf(10)))
    }
}
