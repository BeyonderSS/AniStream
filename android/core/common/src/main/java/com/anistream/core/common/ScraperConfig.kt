package com.anistream.core.common

/**
 * Scraper constants (hosts, keys, versions). The values below are what shipped; ScraperConfigUpdater (core:network)
 * replaces them with the signed, published scrapers.json. Scrapers must read them at call time, never cache them.
 */
object ScraperConfig {
    @Volatile var hianimeBase = "https://hianime.at"
    @Volatile var embedKey = "otaku-embed-v1"
    @Volatile var hianimeServer = "ZokoAnime"

    @Volatile var movieBoxSecret: ByteArray = intArrayOf(
        0xef, 0xa8, 0x91, 0x97, 0x4e, 0xec, 0xd3, 0x14, 0x8d, 0xf6, 0x3a, 0xa6, 0x11, 0x60, 0x2d,
        0xef, 0xd1, 0x01, 0x25, 0x9b, 0xa5, 0x21, 0x02, 0x2c, 0x57, 0xae, 0x05, 0x66, 0xbd, 0x8e
    ).map { it.toByte() }.toByteArray()
    @Volatile var movieBoxHosts: List<String> = listOf(
        "https://api6.aoneroom.com", "https://api5.aoneroom.com", "https://api4.aoneroom.com",
        "https://api4sg.aoneroom.com", "https://api3.aoneroom.com", "https://api6sg.aoneroom.com",
        "https://api.inmoviebox.com"
    )
    @Volatile var movieBoxRetry: Set<Int> = setOf(403, 406, 407, 429, 500, 502, 503, 504)
    @Volatile var movieBoxVersionCodes: List<Int> = listOf(50020117, 50020118, 50020119, 50020120, 50020121)
    @Volatile var movieBoxAndroid: List<Pair<String, String>> = listOf(
        "10" to "QP1A.191005.007.A3", "11" to "RP1A.200720.011", "12" to "S1B.220414.015", "13" to "TQ2A.230405.003"
    )
    @Volatile var movieBoxModels: List<String> = listOf("23078RKD5C", "2201117TY", "2201117TG", "22101316G", "M2012K11AG")
    @Volatile var movieBoxPackage = "com.community.oneroom"
    @Volatile var movieBoxVersionName = "4.0.01.0813.03"
    @Volatile var movieBoxSpCode = "40401"
    @Volatile var movieBoxCronet = "135.0.7012.3"

    @Volatile var dramachiBase = "https://api.nodeobjects.com/"
    @Volatile var dramachiImageCdn = "https://static.nodeobjects.com/thumbnail/"

    @Volatile var fourKHdHubBase = "https://4khdhub.one"
    @Volatile var fourKHdHubMirrors: List<String> = listOf("hubcloud.", "hubdrive.", "greenmotors.", "greenmountmotors.", "pixeldrain.")

    /** Changes whenever a value that feeds MovieBox's persisted client identity changes, so the identity is rebuilt. */
    fun movieBoxIdentityKey(): String = listOf(
        movieBoxVersionCodes, movieBoxAndroid, movieBoxModels, movieBoxPackage, movieBoxVersionName, movieBoxSpCode, movieBoxCronet
    ).joinToString("|").hashCode().toString()
}
