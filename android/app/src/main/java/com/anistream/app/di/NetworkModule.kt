package com.anistream.app.di

import android.content.Context
import com.anistream.core.network.AnidbHttpClient
import com.anistream.core.network.CloudflareBypassEngine
import com.anistream.core.network.CookieJarHolder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideCloudflareBypassEngine(
        @ApplicationContext context: Context
    ): CloudflareBypassEngine {
        return CloudflareBypassEngine(context)
    }

    @Provides
    @Singleton
    fun provideAnidbHttpClient(
        cloudflareBypass: CloudflareBypassEngine,
        cookieJarHolder: CookieJarHolder
    ): AnidbHttpClient {
        return AnidbHttpClient(cloudflareBypass, cookieJarHolder)
    }
}
