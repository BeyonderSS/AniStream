package com.anistream.core.common

import android.app.ActivityManager
import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build

/**
 * Low-end mode for weak TV sticks (Fire TV Stick 2nd gen: API 22, ~1 GB RAM, no HEVC).
 * Auto-on when the device is low-RAM, has < 1.5 GB total RAM or runs API < 24; the TV player settings can override it.
 * Only the tv flavor opts in ([eligible]); the phone build never sees low-end mode.
 */
object LowEnd {
    private const val PREFS = "app"
    private const val KEY = "low_end_mode"

    /** Set by the Application of the tv flavor. */
    @Volatile var eligible = false

    fun auto(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        return Build.VERSION.SDK_INT < 24 || am.isLowRamDevice || mem.totalMem < 1_500L * 1024 * 1024
    }

    fun isOn(context: Context): Boolean {
        if (!eligible) return false
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return if (prefs.contains(KEY)) prefs.getBoolean(KEY, false) else auto(context)
    }

    fun set(context: Context, on: Boolean) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()

    private class Caps(val hwHevc: Boolean, val main10: Boolean, val maxWidth: Int, val maxHeight: Int)

    /** Decoder capabilities from MediaCodecList; pre-29 has no isHardwareAccelerated, so software codecs are recognised by name. */
    private val caps: Caps by lazy {
        runCatching {
            val decoders = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { !it.isEncoder }
            fun hw(info: MediaCodecInfo) = if (Build.VERSION.SDK_INT >= 29) info.isHardwareAccelerated
                else !info.name.startsWith("OMX.google.", true) && !info.name.startsWith("c2.android.", true)
            fun video(mime: String) = decoders.filter { hw(it) && it.supportedTypes.any { t -> t.equals(mime, true) } }
                .map { it.getCapabilitiesForType(mime) }
            val hevc = video(MediaFormat.MIMETYPE_VIDEO_HEVC)
            val all = hevc + video(MediaFormat.MIMETYPE_VIDEO_AVC)
            Caps(
                hwHevc = hevc.isNotEmpty(),
                main10 = hevc.any { c -> c.profileLevels.any { it.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10 } },
                maxWidth = all.maxOfOrNull { it.videoCapabilities.supportedWidths.upper } ?: 1920,
                maxHeight = all.maxOfOrNull { it.videoCapabilities.supportedHeights.upper } ?: 1088
            )
        }.getOrDefault(Caps(hwHevc = false, main10 = false, maxWidth = 1920, maxHeight = 1088))
    }

    val hasHardwareHevc: Boolean get() = caps.hwHevc
    val hasHevcMain10: Boolean get() = caps.main10
    /** Largest frame the hardware decoders take (Fire TV Stick 2: 1920x1088). */
    val maxWidth: Int get() = caps.maxWidth
    val maxHeight: Int get() = caps.maxHeight

    fun isHevc(codecOrLabel: String?): Boolean =
        codecOrLabel != null && listOf("hevc", "h265", "h.265", "x265").any { codecOrLabel.contains(it, true) }

    /** 10-bit / HDR / Dolby Vision by label: needs HEVC Main10 (or better) in hardware. */
    fun isHighBitDepth(codecOrLabel: String?): Boolean = codecOrLabel != null && (
        listOf("10bit", "10-bit", "10 bit", "hdr", "main10", "main 10", "x265 10", "dolby vision").any { codecOrLabel.contains(it, true) } ||
            Regex("""\bDV\b""").containsMatchIn(codecOrLabel)
        )
}
