package com.anistream.domain.usecase

import com.anistream.core.common.Result
import com.anistream.domain.model.FavoriteAnime
import com.anistream.domain.repository.FavoriteRepository

class GetFavoritesUseCase(private val repository: FavoriteRepository) {
    suspend operator fun invoke(): Result<List<FavoriteAnime>> {
        return repository.getAll()
    }
}
