package com.anistream.domain.usecase

import com.anistream.core.common.Result
import com.anistream.domain.model.Genre
import com.anistream.domain.repository.AnidbRepository

class GetGenresUseCase(private val repository: AnidbRepository) {
    suspend operator fun invoke(): Result<List<Genre>> {
        return repository.getGenres()
    }
}
