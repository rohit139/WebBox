package com.webbox.tv.webview

import com.webbox.tv.webview.NavigationHandler.Decision
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationPolicyTest {

    private val handler = NavigationHandler(
        configuredBaseUrl = "https://cinezo.org/",
        allowExternalNavigation = false
    )

    @Test
    fun popupBlockedWhenPopupsDisabled() {
        assertFalse(handler.shouldOpenPopup(false))
    }

    @Test
    fun popupAllowedWhenPopupsEnabled() {
        assertTrue(handler.shouldOpenPopup(true))
    }

    @Test
    fun sameSiteNavigationAllowed() {
        assertTrue(handler.shouldOverrideUrlLoading("https://cinezo.org/movies") is Decision.Allow)
    }

    @Test
    fun subdomainOfConfiguredSiteAllowed() {
        assertTrue(handler.shouldOverrideUrlLoading("https://cdn.cinezo.org/v.mp4") is Decision.Allow)
    }

    @Test
    fun externalRedirectBlocked() {
        assertTrue(
            handler.shouldOverrideUrlLoading("https://evil-captcha.example/redirect") is Decision.Block
        )
    }

    @Test
    fun mediaCdnAllowed() {
        assertTrue(handler.shouldOverrideUrlLoading("https://cdn.example.com/video.mp4") is Decision.Allow)
    }

    @Test
    fun nonWebSchemeBlocked() {
        assertTrue(handler.shouldOverrideUrlLoading("intent://example.com#Intent;end") is Decision.Block)
    }
}