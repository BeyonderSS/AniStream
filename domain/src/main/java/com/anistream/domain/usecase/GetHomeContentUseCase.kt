package com.anistream.domain.usecase

import com.anistream.core.common.Result
import com.anistream.domain.repository.AnidbRepository
import com.anistream.domain.repository.HomeContent

class GetHomeContentUseCase(private val repository: AnidbRepository) {
    suspend operator fun invoke(): Result<HomeContent> = repository.getHomeContent()
}
