package com.anistream.core.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun <T> safeApiCall(
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    block: suspend () -> T
): Result<T> {
    return withContext(dispatcher) {
        try {
            Result.success(block())
        } catch (e: Exception) {
            Result.error(e)
        }
    }
}
