package com.webbox.tv.util

import java.net.URI
import java.util.Locale

object UrlValidator {

    private val blockedSchemes = setOf(
        "file", "javascript", "intent", "content", "data", "about", "blob", "ftp"
    )

    private val hostRegex = Regex("^[a-zA-Z0-9][a-zA-Z0-9\\-_.]*[a-zA-Z0-9]$|^[a-zA-Z0-9]$")

    data class Result(
        val isValid: Boolean,
        val normalizedUrl: String = "",
        val errorMessage: String = ""
    )

    fun validate(rawInput: String): Result {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            return Result(false, errorMessage = "URL cannot be empty.")
        }

        if (trimmed.contains(Regex("\\s"))) {
            return Result(false, errorMessage = genericError())
        }

        val withScheme = when {
            trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            "://" in trimmed -> trimmed
            else -> "https://$trimmed"
        }

        val uri = try {
            URI.create(withScheme)
        } catch (_: Exception) {
            return Result(false, errorMessage = genericError())
        }

        val scheme = uri.scheme?.lowercase(Locale.US)
        if (scheme == null || scheme !in setOf("http", "https")) {
            if (scheme != null && scheme in blockedSchemes) {
                return Result(false, errorMessage = genericError())
            }
            return Result(false, errorMessage = genericError())
        }

        val host = uri.host
        if (host.isNullOrBlank()) {
            return Result(false, errorMessage = genericError())
        }

        if (host != "localhost" && !host.matches(hostRegex)) {
            return Result(false, errorMessage = genericError())
        }

        // Normalize scheme to lowercase, preserving the rest of the URL
        val normalized = if (withScheme.startsWith(scheme, ignoreCase = true)) {
            scheme + withScheme.substring(scheme.length)
        } else {
            withScheme
        }

        return Result(true, normalizedUrl = normalized)
    }

    fun extractBaseHost(url: String): String? {
        return try {
            URI.create(url).host?.lowercase(Locale.US)
        } catch (_: Exception) {
            null
        }
    }

    fun isSameSite(baseUrl: String, targetUrl: String): Boolean {
        val baseHost = extractBaseHost(baseUrl) ?: return false
        val targetHost = extractBaseHost(targetUrl) ?: return false
        if (baseHost == targetHost) return true
        // Allow subdomains of the configured base host
        return targetHost.endsWith(".$baseHost")
    }

    private fun genericError(): String =
        "Invalid website URL.\nPlease enter a valid HTTP or HTTPS URL."
}
