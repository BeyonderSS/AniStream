package com.anistream.domain.usecase

import com.anistream.core.common.Result
import com.anistream.domain.model.Episode
import com.anistream.domain.repository.AnidbRepository

class GetEpisodesUseCase(private val repository: AnidbRepository) {
    suspend operator fun invoke(slug: String): Result<List<Episode>> {
        return repository.getEpisodes(slug)
    }
}
