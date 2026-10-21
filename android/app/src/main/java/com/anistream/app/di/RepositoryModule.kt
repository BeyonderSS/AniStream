package com.anistream.app.di

import com.anistream.domain.repository.AnidbRepository
import com.anistream.domain.repository.FavoriteRepository
import com.anistream.domain.repository.WatchHistoryRepository
import com.anistream.domain.usecase.GetAnimeDetailUseCase
import com.anistream.domain.usecase.GetContinueWatchingUseCase
import com.anistream.domain.usecase.GetEpisodesUseCase
import com.anistream.domain.usecase.GetFavoritesUseCase
import com.anistream.domain.usecase.GetGenresUseCase
import com.anistream.domain.usecase.GetHomeContentUseCase
import com.anistream.domain.usecase.GetScheduleUseCase
import com.anistream.domain.usecase.ResolveStreamUseCase
import com.anistream.domain.usecase.SaveWatchProgressUseCase
import com.anistream.domain.usecase.SearchAnimeUseCase
import com.anistream.domain.usecase.SelectQualityUseCase
import com.anistream.domain.usecase.ToggleFavoriteUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideAnidbRepository(impl: com.anistream.core.network.repository.AnidbRepositoryImpl): AnidbRepository = impl

    @Provides
    @Singleton
    fun provideWatchHistoryRepository(impl: com.anistream.core.network.repository.WatchHistoryRepositoryImpl): WatchHistoryRepository = impl

    @Provides
    @Singleton
    fun provideFavoriteRepository(impl: com.anistream.core.network.repository.FavoriteRepositoryImpl): FavoriteRepository = impl

    @Provides
    @Singleton
    fun provideDownloadRepository(impl: com.anistream.core.network.repository.DownloadRepositoryImpl): com.anistream.domain.repository.DownloadRepository = impl
}

@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {

    @Provides
    fun provideSelectQualityUseCase() = SelectQualityUseCase()

    @Provides
    fun provideSearchAnimeUseCase(repo: AnidbRepository) = SearchAnimeUseCase(repo)

    @Provides
    fun provideGetAnimeDetailUseCase(repo: AnidbRepository) = GetAnimeDetailUseCase(repo)

    @Provides
    fun provideGetEpisodesUseCase(repo: AnidbRepository) = GetEpisodesUseCase(repo)

    @Provides
    fun provideResolveStreamUseCase(
        repo: AnidbRepository,
        selectQuality: SelectQualityUseCase
    ) = ResolveStreamUseCase(repo, selectQuality)

    @Provides
    fun provideGetHomeContentUseCase(repo: AnidbRepository) = GetHomeContentUseCase(repo)

    @Provides
    fun provideGetContinueWatchingUseCase(repo: WatchHistoryRepository) = GetContinueWatchingUseCase(repo)

    @Provides
    fun provideToggleFavoriteUseCase(repo: FavoriteRepository) = ToggleFavoriteUseCase(repo)

    @Provides
    fun provideGetFavoritesUseCase(repo: FavoriteRepository) = GetFavoritesUseCase(repo)

    @Provides
    fun provideSaveWatchProgressUseCase(repo: WatchHistoryRepository) = SaveWatchProgressUseCase(repo)

    @Provides
    fun provideGetScheduleUseCase(repo: AnidbRepository) = GetScheduleUseCase(repo)

    @Provides
    fun provideGetGenresUseCase(repo: AnidbRepository) = GetGenresUseCase(repo)
}
