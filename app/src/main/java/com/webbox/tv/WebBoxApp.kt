package com.webbox.tv

import android.app.Application
import android.webkit.WebView
import com.webbox.tv.data.SettingsRepository

class WebBoxApp : Application() {
    lateinit var settingsRepository: SettingsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
        settingsRepository = SettingsRepository(this)
    }
}
