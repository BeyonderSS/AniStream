package com.anistream.core.database

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Which profile's watch history the app reads and writes ("" = local-only, signed out).
 * Set by the account layer; history writes ping [changes] so sync can push them.
 */
object ActiveProfile {
    @Volatile var id: String = ""
    val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
}
