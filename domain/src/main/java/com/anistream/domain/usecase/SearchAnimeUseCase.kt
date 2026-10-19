package com.anistream.domain.usecase

import com.anistream.core.common.Result
import com.anistream.domain.model.AnimeSearchResult
import com.anistream.domain.repository.AnidbRepository

class SearchAnimeUseCase(private val repository: AnidbRepository) {
    suspend operator fun invoke(query: String): Result<List<AnimeSearchResult>> {
        if (query.isBlank()) return Result.success(emptyList())
        return repository.searchAnime(query)
    }
}
