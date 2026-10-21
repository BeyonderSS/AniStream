package com.anistream.core.network

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CookieJarHolder @Inject constructor() {
    private val cookieStore = mutableMapOf<String, List<Cookie>>()
    private var userAgent: String? = null

    fun getCookies(): String? {
        return cookieStore.values.flatten()
            .joinToString("; ") { "${it.name}=${it.value}" }
            .ifEmpty { null }
    }

    fun getUserAgent(): String? = userAgent

    fun setCookies(cookiesString: String, url: String = com.anistream.core.common.ScraperConfig.hianimeBase) {
        val httpUrl = url.toHttpUrlOrNull() ?: return
        val cookies = cookiesString.split("; ").mapNotNull { cookieStr ->
            val parts = cookieStr.split("=", limit = 2)
            if (parts.size == 2) {
                Cookie.Builder()
                    .domain(httpUrl.host)
                    .path("/")
                    .name(parts[0].trim())
                    .value(parts[1].trim())
                    .build()
            } else null
        }
        cookieStore[httpUrl.host] = cookies
    }

    fun setUserAgent(ua: String) {
        userAgent = ua
    }

    fun clear() {
        cookieStore.clear()
        userAgent = null
    }
}

@Singleton
class AnidbCookieJar @Inject constructor(
    private val holder: CookieJarHolder
) : CookieJar {

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val existing = cookieStore()[url.host]?.toMutableList() ?: mutableListOf()
        existing.addAll(cookies)
        cookieStore()[url.host] = existing
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return cookieStore()[url.host] ?: emptyList()
    }

    private fun cookieStore(): MutableMap<String, MutableList<Cookie>> {
        val field = CookieJarHolder::class.java.getDeclaredField("cookieStore")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return field.get(holder) as MutableMap<String, MutableList<Cookie>>
    }
}
