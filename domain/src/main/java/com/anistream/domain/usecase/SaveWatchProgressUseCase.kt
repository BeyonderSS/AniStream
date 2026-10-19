package com.anistream.domain.usecase

import com.anistream.core.common.Result
import com.anistream.domain.repository.WatchHistoryRepository

class SaveWatchProgressUseCase(private val repository: WatchHistoryRepository) {
    suspend operator fun invoke(
        slug: String,
        episode: Int,
        progress: Float
    ): Result<Unit> {
        return repository.updateProgress(slug, episode, progress)
    }
}
