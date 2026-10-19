package com.anistream.core.parser

import com.anistream.core.common.Result

internal inline fun <T> parseResult(crossinline block: () -> T): Result<T> {
    return try {
        Result.success(block())
    } catch (e: Exception) {
        Result.error(e)
    }
}
