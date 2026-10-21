package com.anistream.core.network

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

data class ClearanceResult(
    val cookies: String,
    val userAgent: String
)

@Singleton
class CloudflareBypassEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var cachedCookies: String? = null
    private var cachedUserAgent: String? = null
    private var cacheTimestamp: Long = 0

    private companion object {
        const val CACHE_DURATION_MS = 30 * 60 * 1000L // 30 minutes
        const val CHALLENGE_TIMEOUT_MS = 20_000L
        const val CHROME_USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
    }

    fun hasValidCache(): Boolean {
        return cachedCookies != null &&
            (System.currentTimeMillis() - cacheTimestamp) < CACHE_DURATION_MS
    }

    fun getCachedCookies(): String? = cachedCookies
    fun getCachedUserAgent(): String? = cachedUserAgent

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun resolveClearance(url: String): ClearanceResult? = withContext(Dispatchers.Main) {
        if (hasValidCache()) {
            return@withContext ClearanceResult(cachedCookies!!, cachedUserAgent!!)
        }

        withTimeoutOrNull(CHALLENGE_TIMEOUT_MS) {
        suspendCancellableCoroutine<ClearanceResult?> { cont ->
            val webView = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.userAgentString = CHROME_USER_AGENT
                settings.domStorageEnabled = true

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, loadUrl: String?) {
                        val cookieManager = CookieManager.getInstance()
                        val cookies = cookieManager.getCookie(url)
                            ?: cookieManager.getCookie(com.anistream.core.common.ScraperConfig.hianimeBase)

                        if (cookies != null && cookies.contains("cf_clearance")) {
                            cachedCookies = cookies
                            cachedUserAgent = settings.userAgentString
                            cacheTimestamp = System.currentTimeMillis()
                            view?.destroy()
                            if (cont.isActive) {
                                cont.resume(ClearanceResult(cookies, settings.userAgentString))
                            }
                        } else if (cookies != null && !cookies.contains("challenge-platform")) {
                            cachedCookies = cookies
                            cachedUserAgent = settings.userAgentString
                            cacheTimestamp = System.currentTimeMillis()
                            view?.destroy()
                            if (cont.isActive) {
                                cont.resume(ClearanceResult(cookies, settings.userAgentString))
                            }
                        }
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        errorCode: Int,
                        description: String?,
                        failingUrl: String?
                    ) {
                        view?.destroy()
                        if (cont.isActive) {
                            cont.resume(null as ClearanceResult?)
                        }
                    }
                }
            }

            cont.invokeOnCancellation {
                webView.destroy()
            }

            webView.loadUrl(url)
        }
        }
    }

    fun invalidateCache() {
        cachedCookies = null
        cachedUserAgent = null
        cacheTimestamp = 0
    }
}
