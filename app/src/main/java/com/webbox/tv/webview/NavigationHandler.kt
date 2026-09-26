package com.webbox.tv.webview

import android.util.Log
import com.webbox.tv.BuildConfig
import com.webbox.tv.util.UrlValidator
import java.net.URI
import java.util.Locale

class NavigationHandler(
    private var configuredBaseUrl: String,
    private var allowExternalNavigation: Boolean
) {
    fun updatePolicy(baseUrl: String, allowExternal: Boolean) {
        configuredBaseUrl = baseUrl
        allowExternalNavigation = allowExternal
    }

    sealed class Decision {
        data object Allow : Decision()
        data class Block(val reason: String) : Decision()
    }

    fun shouldOverrideUrlLoading(url: String?): Decision {
        if (url.isNullOrBlank()) return Decision.Block("Empty URL")

        val scheme = try {
            URI.create(url ?: "").scheme?.lowercase(Locale.US)
        } catch (_: Exception) {
            null
        }

        if (scheme == null) {
            return Decision.Allow
        }

        // Allow about:blank and common in-page schemes used by players
        if (scheme == "about" || scheme == "blob" || scheme == "data") {
            return Decision.Allow
        }

        if (scheme != "http" && scheme != "https") {
            logNav(url, blocked = true)
            return Decision.Block("Blocked non-web scheme: $scheme")
        }

        if (allowExternalNavigation) {
            logNav(url, blocked = false)
            return Decision.Allow
        }

        if (UrlValidator.isSameSite(configuredBaseUrl, url)) {
            logNav(url, blocked = false)
            return Decision.Allow
        }

        // Allow common media/CDN hosts that sites often need for video
        if (isLikelyMediaCdn(url)) {
            logNav(url, blocked = false)
            return Decision.Allow
        }

        logNav(url, blocked = true)
        return Decision.Block("External link blocked")
    }

    fun shouldOpenPopup(allowPopups: Boolean): Boolean = allowPopups

    private fun isLikelyMediaCdn(url: String): Boolean {
        val host = UrlValidator.extractBaseHost(url) ?: return false
        val mediaHints = listOf(
            "googlevideo.com",
            "gvt1.com",
            "ytimg.com",
            "youtube.com",
            "youtu.be",
            "cloudfront.net",
            "akamaized.net",
            "akamaihd.net",
            "fastly.net",
            "cloudflare",
            "themoviedb.org",
            "tmdb.org",
            "cdn.",
            "video.",
            "media.",
            "stream.",
            "hls.",
            "embed",
            "m3u8",
            "vidsrc",
            "vidlink",
            "filemoon",
            "streamtape",
            "streamwish",
            "dood",
            "mixdrop",
            "megacloud",
            "rabbitstream",
            "mcloud",
            "vidplay",
            "autoembed",
            "2embed",
            "smashy",
            "hydrax"
        )
        val lowerUrl = url.lowercase(Locale.US)
        return mediaHints.any { host.contains(it) || lowerUrl.contains(it) }
    }

    private fun logNav(url: String, blocked: Boolean) {
        if (BuildConfig.DEBUG) {
            val status = if (blocked) "BLOCKED" else "ALLOW"
            Log.d(TAG, "Navigation: $url -> $status")
        }
    }

    companion object {
        private const val TAG = "WebBoxNav"
    }
}
