package com.anistream.feature.party

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PartyTest {
    @Test fun tagMapsToVersionCode() {
        assertEquals(107, tagToVersionCode("v1.0.7"))
        assertEquals(100, tagToVersionCode("1.0.0"))
        assertEquals(112, tagToVersionCode(" v1.0.12 "))
        assertNull(tagToVersionCode("v2.0.1"))
        assertNull(tagToVersionCode("v1.0.7-beta"))
        assertNull(tagToVersionCode("nightly"))
    }

    @Test fun assetSelectionSeparatesFlavors() {
        val assets = listOf(
            "Reelm-TV-v1.0.7.apk" to "tv-url",
            "Reelm-v1.0.7.apk" to "phone-url",
            "Reelm-v1.0.7.apk.sha256" to "sum-url"
        )
        assertEquals("phone-url", pickAsset(assets, tv = false))
        assertEquals("tv-url", pickAsset(assets, tv = true))
        assertNull(pickAsset(listOf("source.zip" to "z"), tv = false))
    }

    @Test fun offerRules() {
        assertTrue(shouldOffer(106, 107, debuggable = false))
        assertFalse(shouldOffer(107, 107, debuggable = false))
        assertFalse(shouldOffer(120, 107, debuggable = false)) // local build above latest
        assertFalse(shouldOffer(1, 107, debuggable = false)) // local build default versionCode
        assertFalse(shouldOffer(106, 107, debuggable = true))
    }

    @Test fun releaseJsonPicksFlavorAsset() {
        val json = Json.parseToJsonElement(
            """{"tag_name":"v1.0.9","name":"Reelm 9","body":"Fixes","assets":[
              {"name":"Reelm-TV-v1.0.9.apk","browser_download_url":"https://x/tv.apk","size":5},
              {"name":"Reelm-v1.0.9.apk","browser_download_url":"https://x/phone.apk","size":7}]}"""
        ).jsonObject
        assertEquals("https://x/phone.apk", parseRelease(json, tv = false)?.apkUrl)
        assertEquals("https://x/tv.apk", parseRelease(json, tv = true)?.apkUrl)
        assertEquals(109, parseRelease(json, tv = true)?.versionCode)
    }

    @Test fun prereleaseIsNeverOffered() {
        val assets = """"assets":[{"name":"Reelm-v1.0.9.apk","browser_download_url":"https://x/phone.apk","size":7}]"""
        val pre = Json.parseToJsonElement("""{"tag_name":"v1.0.9","prerelease":true,$assets}""").jsonObject
        assertNull(parseRelease(pre, tv = false))
        // CI names feature builds v1.0.<run>-<branch>, which the tag pattern rejects as well
        assertNull(tagToVersionCode("v1.0.18-port-automation"))
        assertEquals(109, parseRelease(Json.parseToJsonElement("""{"tag_name":"v1.0.9","prerelease":false,$assets}""").jsonObject, tv = false)?.versionCode)
    }

    @Test fun channelComesFromTheVersionName() {
        assertNull(channelOf("1.0.22"))
        assertNull(channelOf("1.0.22-tv")) // the TV flavor suffix is not a branch
        assertEquals("port-automation", channelOf("1.0.22-port-automation"))
        assertEquals("port-automation", channelOf("1.0.22-port-automation-tv"))
        assertNull(channelOf("1.0.0"))
    }

    @Test fun featureBuildsFollowOnlyTheirOwnPreReleasesAndStable() {
        fun release(tag: String, pre: Boolean) = Json.parseToJsonElement(
            """{"tag_name":"$tag","prerelease":$pre,"assets":[{"name":"Reelm-v1.apk","browser_download_url":"https://x/$tag.apk","size":1}]}"""
        ).jsonObject
        val list = kotlinx.serialization.json.JsonArray(
            listOf(
                release("v1.0.30-other-branch", true), // another branch: never
                release("v1.0.25-my-branch", true),
                release("v1.0.24", false),
                release("v1.0.20-my-branch", true)
            )
        )
        // newest of: stable + my-branch pre-releases (the other branch's v1.0.30 is skipped)
        assertEquals(125, pickNewest(list, tv = false, channel = "my-branch")?.versionCode)
        // a stable build only ever sees stable releases
        assertEquals(124, pickNewest(list, tv = false, channel = null)?.versionCode)
        // a newer stable release wins over an older pre-release of the branch
        val newerStable = kotlinx.serialization.json.JsonArray(listOf(release("v1.0.26", false), release("v1.0.25-my-branch", true)))
        assertEquals(126, pickNewest(newerStable, tv = false, channel = "my-branch")?.versionCode)
    }

    @Test fun playingRoomAdvancesByElapsedServerTime() {
        assertEquals(12_500L, targetPosition(10_000, at = 1_000, serverNow = 3_500, playing = true))
        assertEquals(10_000L, targetPosition(10_000, at = 1_000, serverNow = 3_500, playing = false))
        // clock skew can put "now" before "at": never seek backwards
        assertEquals(10_000L, targetPosition(10_000, at = 2_000, serverNow = 1_900, playing = true))
    }

    @Test fun driftThreshold() {
        assertFalse(shouldSeek(10_000, 11_499))
        assertTrue(shouldSeek(10_000, 11_500))
        assertTrue(shouldSeek(12_000, 10_000))
    }

    @Test fun echoDetection() {
        assertTrue(isEcho("me", "me"))
        assertFalse(isEcho("you", "me"))
        assertFalse(isEcho("me", null))
    }
}
