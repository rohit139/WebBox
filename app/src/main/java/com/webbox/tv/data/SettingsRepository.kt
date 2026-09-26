package com.webbox.tv.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getSettings(): AppSettings {
        return AppSettings(
            websiteUrl = prefs.getString(KEY_WEBSITE_URL, AppSettings.DEFAULT_URL)
                ?: AppSettings.DEFAULT_URL,
            fullscreenEnabled = prefs.getBoolean(KEY_FULLSCREEN, true),
            landscapeEnabled = prefs.getBoolean(KEY_LANDSCAPE, true),
            dpadNavigationEnabled = prefs.getBoolean(KEY_DPAD_NAV, true),
            allowExternalNavigation = prefs.getBoolean(KEY_ALLOW_EXTERNAL, false),
            allowPopups = prefs.getBoolean(KEY_ALLOW_POPUPS, false),
            rememberLastPage = prefs.getBoolean(KEY_REMEMBER_LAST, false),
            lastPageUrl = prefs.getString(KEY_LAST_PAGE, "") ?: "",
            lastNavigationError = prefs.getString(KEY_LAST_ERROR, "") ?: ""
        )
    }

    fun saveSettings(settings: AppSettings) {
        prefs.edit {
            putString(KEY_WEBSITE_URL, settings.websiteUrl)
            putBoolean(KEY_FULLSCREEN, settings.fullscreenEnabled)
            putBoolean(KEY_LANDSCAPE, settings.landscapeEnabled)
            putBoolean(KEY_DPAD_NAV, settings.dpadNavigationEnabled)
            putBoolean(KEY_ALLOW_EXTERNAL, settings.allowExternalNavigation)
            putBoolean(KEY_ALLOW_POPUPS, settings.allowPopups)
            putBoolean(KEY_REMEMBER_LAST, settings.rememberLastPage)
            putString(KEY_LAST_PAGE, settings.lastPageUrl)
            putString(KEY_LAST_ERROR, settings.lastNavigationError)
        }
    }

    fun saveWebsiteUrl(url: String) {
        prefs.edit { putString(KEY_WEBSITE_URL, url) }
    }

    fun saveLastPageUrl(url: String) {
        prefs.edit { putString(KEY_LAST_PAGE, url) }
    }

    fun saveLastError(error: String) {
        prefs.edit { putString(KEY_LAST_ERROR, error) }
    }

    fun resetToDefaults() {
        prefs.edit { clear() }
        saveSettings(AppSettings())
    }

    fun getLoadUrl(): String {
        val settings = getSettings()
        return if (settings.rememberLastPage && settings.lastPageUrl.isNotBlank()) {
            settings.lastPageUrl
        } else {
            settings.websiteUrl.ifBlank { AppSettings.DEFAULT_URL }
        }
    }

    companion object {
        private const val PREFS_NAME = "webbox_settings"
        private const val KEY_WEBSITE_URL = "website_url"
        private const val KEY_FULLSCREEN = "fullscreen_enabled"
        private const val KEY_LANDSCAPE = "landscape_enabled"
        private const val KEY_DPAD_NAV = "dpad_navigation_enabled"
        private const val KEY_ALLOW_EXTERNAL = "allow_external_navigation"
        private const val KEY_ALLOW_POPUPS = "allow_popups"
        private const val KEY_REMEMBER_LAST = "remember_last_page"
        private const val KEY_LAST_PAGE = "last_page_url"
        private const val KEY_LAST_ERROR = "last_navigation_error"
    }
}
