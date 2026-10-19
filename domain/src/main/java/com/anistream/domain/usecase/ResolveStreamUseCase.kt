package com.anistream.domain.usecase

import com.anistream.core.common.Result
import com.anistream.domain.model.StreamSource
import com.anistream.domain.repository.AnidbRepository

class ResolveStreamUseCase(
    private val repository: AnidbRepository,
    private val selectQuality: SelectQualityUseCase
) {
    suspend operator fun invoke(
        episodeId: Long,
        language: String = "sub",
        preferredQuality: String = "best"
    ): Result<StreamSource> {
        return when (val result = repository.resolveStream(episodeId, language)) {
            is Result.Success -> {
                val variants = result.data
                if (variants.isEmpty()) {
                    Result.error(IllegalStateException("No streams found"))
                } else {
                    Result.success(selectQuality(variants, preferredQuality))
                }
            }
            is Result.Error -> result
            is Result.Loading -> Result.loading()
        }
    }

    suspend fun getAllQualities(
        episodeId: Long,
        language: String = "sub"
    ): Result<List<StreamSource>> {
        return repository.resolveStream(episodeId, language)
    }
}
