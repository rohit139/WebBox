package com.webbox.tv.ui

import android.os.Build
import android.os.Bundle
import android.webkit.WebView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.webbox.tv.R
import com.webbox.tv.WebBoxApp
import com.webbox.tv.util.NetworkUtils

class DiagnosticsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_diagnostics)

        val repo = (application as WebBoxApp).settingsRepository
        val settings = repo.getSettings()

        val webViewVersion = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WebView.getCurrentWebViewPackage()?.versionName ?: "unknown"
            } else {
                "API ${Build.VERSION.SDK_INT}"
            }
        } catch (_: Exception) {
            "unknown"
        }

        val connected = NetworkUtils.isNetworkAvailable(this)

        findViewById<TextView>(R.id.diagCurrentUrl).text =
            "${getString(R.string.current_url)}: ${settings.websiteUrl}"
        findViewById<TextView>(R.id.diagWebViewVersion).text =
            "${getString(R.string.webview_version)}: $webViewVersion"
        findViewById<TextView>(R.id.diagAndroidVersion).text =
            "${getString(R.string.android_version)}: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        findViewById<TextView>(R.id.diagDeviceModel).text =
            "${getString(R.string.device_model)}: ${Build.MANUFACTURER} ${Build.MODEL}"
        findViewById<TextView>(R.id.diagConnection).text =
            "${getString(R.string.connection_status)}: " +
                if (connected) getString(R.string.connected) else getString(R.string.disconnected)
        findViewById<TextView>(R.id.diagLastError).text =
            "${getString(R.string.last_navigation_error)}: " +
                settings.lastNavigationError.ifBlank { getString(R.string.none) }

        findViewById<MaterialButton>(R.id.btnDiagBack).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.btnDiagBack).requestFocus()
    }
}
