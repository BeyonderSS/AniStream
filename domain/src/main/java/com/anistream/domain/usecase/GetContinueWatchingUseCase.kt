package com.anistream.domain.usecase

import com.anistream.core.common.Result
import com.anistream.domain.model.WatchHistory
import com.anistream.domain.repository.WatchHistoryRepository

class GetContinueWatchingUseCase(private val repository: WatchHistoryRepository) {
    /** defaults to anime so the anime Home row never gets "mb:" media rows it can't open; null = everything */
    suspend operator fun invoke(kind: String? = "anime"): Result<List<WatchHistory>> {
        return repository.getContinueWatching(kind)
    }
}
