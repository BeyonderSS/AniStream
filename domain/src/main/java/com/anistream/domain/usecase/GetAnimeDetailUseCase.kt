package com.anistream.domain.usecase

import com.anistream.core.common.Result
import com.anistream.domain.model.AnimeDetail
import com.anistream.domain.repository.AnidbRepository

class GetAnimeDetailUseCase(private val repository: AnidbRepository) {
    suspend operator fun invoke(slug: String): Result<AnimeDetail> {
        return repository.getAnimeDetail(slug)
    }
}
