package com.webbox.tv.data

data class AppSettings(
    val websiteUrl: String = DEFAULT_URL,
    val fullscreenEnabled: Boolean = true,
    val landscapeEnabled: Boolean = true,
    val dpadNavigationEnabled: Boolean = true,
    val allowExternalNavigation: Boolean = false,
    val allowPopups: Boolean = false,
    val rememberLastPage: Boolean = false,
    val lastPageUrl: String = "",
    val lastNavigationError: String = ""
) {
    companion object {
        const val DEFAULT_URL = "https://cinezo.org/"
    }
}
