package com.webbox.tv.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlValidatorTest {

    @Test
    fun acceptsHttpsUrl() {
        val result = UrlValidator.validate("https://example.com")
        assertTrue(result.isValid)
        assertEquals("https://example.com", result.normalizedUrl)
    }

    @Test
    fun acceptsHttpUrl() {
        val result = UrlValidator.validate("http://example.com/path")
        assertTrue(result.isValid)
        assertTrue(result.normalizedUrl.startsWith("http://example.com"))
    }

    @Test
    fun normalizesMissingScheme() {
        val result = UrlValidator.validate("example.com")
        assertTrue(result.isValid)
        assertEquals("https://example.com", result.normalizedUrl)
    }

    @Test
    fun rejectsJavascriptScheme() {
        val result = UrlValidator.validate("javascript:alert(1)")
        assertFalse(result.isValid)
    }

    @Test
    fun rejectsFileScheme() {
        val result = UrlValidator.validate("file:///sdcard/x.html")
        assertFalse(result.isValid)
    }

    @Test
    fun rejectsEmpty() {
        val result = UrlValidator.validate("   ")
        assertFalse(result.isValid)
    }

    @Test
    fun sameSiteMatchesSubdomain() {
        assertTrue(UrlValidator.isSameSite("https://example.com/", "https://cdn.example.com/v.mp4"))
        assertFalse(UrlValidator.isSameSite("https://example.com/", "https://other.com/"))
    }
}
