package com.anistream.domain.usecase

import com.anistream.core.common.Result
import com.anistream.domain.model.WatchHistory
import com.anistream.domain.repository.WatchHistoryRepository

class GetContinueWatchingUseCase(private val repository: WatchHistoryRepository) {
    suspend operator fun invoke(): Result<List<WatchHistory>> {
        return repository.getContinueWatching()
    }
}
