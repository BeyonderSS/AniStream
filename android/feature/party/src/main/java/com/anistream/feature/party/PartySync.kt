package com.anistream.feature.party

import kotlin.math.abs

/** Local and room position may differ by less than this before we bother seeking. */
const val DRIFT_MS = 1500L

/** Where playback should be now for an event stamped at server time [at]; paused rooms stay put. */
fun targetPosition(position: Long, at: Long, serverNow: Long, playing: Boolean): Long =
    if (playing) position + (serverNow - at).coerceAtLeast(0L) else position

fun shouldSeek(local: Long, target: Long) = abs(local - target) >= DRIFT_MS

/** Own broadcast coming back; [self] is null when the server never told us our id. */
fun isEcho(by: String, self: String?) = self != null && by == self
