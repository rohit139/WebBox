package com.webbox.tv.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.webbox.tv.R
import com.webbox.tv.WebBoxApp
import com.webbox.tv.data.AppSettings
import com.webbox.tv.data.SettingsRepository
import com.webbox.tv.databinding.ActivityMainBinding
import com.webbox.tv.webview.WebViewController

class MainActivity : AppCompatActivity(), WebViewController.Callbacks {

    private lateinit var binding: ActivityMainBinding
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var webViewController: WebViewController
    private lateinit var settings: AppSettings

    private val handler = Handler(Looper.getMainLooper())
    private var backPressTime = 0L
    private var longBackRunnable: Runnable? = null
    private var isLongBackTriggered = false
    private var webViewState: Bundle? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsRepository = (application as WebBoxApp).settingsRepository
        settings = settingsRepository.getSettings()

        applyOrientation(settings)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyFullscreen(settings.fullscreenEnabled)

        webViewController = WebViewController(
            context = this,
            webView = binding.webView,
            fullscreenContainer = binding.fullscreenContainer,
            callbacks = this
        )
        webViewController.setup(settings)

        binding.btnRetry.setOnClickListener { reloadCurrent() }
        binding.btnErrorSettings.setOnClickListener { openSettings() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBack()
            }
        })

        if (savedInstanceState != null) {
            webViewState = savedInstanceState.getBundle(KEY_WEBVIEW_STATE)
            val restored = webViewState?.let { webViewController.restoreState(it) } == true
            if (!restored) {
                loadConfiguredUrl()
            }
        } else {
            loadConfiguredUrl()
        }
    }

    override fun onResume() {
        super.onResume()
        val latest = settingsRepository.getSettings()
        val urlChanged = latest.websiteUrl != settings.websiteUrl
        val flagsChanged =
            latest.allowExternalNavigation != settings.allowExternalNavigation ||
                latest.allowPopups != settings.allowPopups ||
                latest.dpadNavigationEnabled != settings.dpadNavigationEnabled ||
                latest.rememberLastPage != settings.rememberLastPage ||
                latest.fullscreenEnabled != settings.fullscreenEnabled ||
                latest.landscapeEnabled != settings.landscapeEnabled

        settings = latest
        applyOrientation(settings)
        applyFullscreen(settings.fullscreenEnabled)
        webViewController.applySettings(settings)
        webViewController.onResume()
        if (binding.errorOverlay.visibility != View.VISIBLE) {
            binding.webView.requestFocus()
        }

        if (urlChanged || intent.getBooleanExtra(EXTRA_FORCE_RELOAD, false)) {
            intent.removeExtra(EXTRA_FORCE_RELOAD)
            loadConfiguredUrl()
        } else if (flagsChanged && webViewController.getCurrentUrl().isNullOrBlank()) {
            loadConfiguredUrl()
        }
    }

    override fun onPause() {
        webViewController.onPause()
        val current = webViewController.getCurrentUrl()
        if (settings.rememberLastPage && !current.isNullOrBlank()) {
            settingsRepository.saveLastPageUrl(current)
        }
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val bundle = Bundle()
        webViewController.saveState(bundle)
        outState.putBundle(KEY_WEBVIEW_STATE, bundle)
    }

    override fun onDestroy() {
        longBackRunnable?.let { handler.removeCallbacks(it) }
        if (::webViewController.isInitialized) {
            // Don't fully destroy WebView on config change; Activity is finishing
            if (isFinishing) {
                // Keep WebView alive managed by system; avoid double-destroy issues
            }
        }
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (event.repeatCount == 0) {
                        isLongBackTriggered = false
                        longBackRunnable = Runnable {
                            isLongBackTriggered = true
                            openSettings()
                        }
                        handler.postDelayed(longBackRunnable!!, LONG_BACK_MS)
                    }
                    return true
                }
                KeyEvent.ACTION_UP -> {
                    longBackRunnable?.let { handler.removeCallbacks(it) }
                    longBackRunnable = null
                    if (!isLongBackTriggered) {
                        handleBack()
                    }
                    return true
                }
            }
        }

        if (isMenuKey(event.keyCode)) {
            if (event.action == KeyEvent.ACTION_UP) {
                openSettings()
            }
            return true
        }

        if (this::binding.isInitialized && binding.errorOverlay.visibility == View.VISIBLE) {
            return super.dispatchKeyEvent(event)
        }

        if (::webViewController.isInitialized && webViewController.isVideoFullscreen()) {
            return super.dispatchKeyEvent(event)
        }

        if (::webViewController.isInitialized && webViewController.handleKeyEvent(event)) {
            return true
        }

        return super.dispatchKeyEvent(event)
    }

    private fun isMenuKey(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_MENU ||
            keyCode == KeyEvent.KEYCODE_SETTINGS ||
            keyCode == KeyEvent.KEYCODE_TV_CONTENTS_MENU
    }

    private fun handleBack() {
        when {
            webViewController.isVideoFullscreen() -> {
                webViewController.exitFullscreen()
            }
            binding.errorOverlay.visibility == View.VISIBLE -> {
                hideError()
                if (webViewController.canGoBack()) {
                    webViewController.goBack()
                } else {
                    maybeExit()
                }
            }
            webViewController.canGoBack() -> {
                webViewController.goBack()
            }
            else -> maybeExit()
        }
    }

    private fun maybeExit() {
        val now = System.currentTimeMillis()
        if (now - backPressTime < 2000) {
            finish()
        } else {
            backPressTime = now
            Toast.makeText(this, "Press BACK again to exit", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadConfiguredUrl() {
        hideError()
        showLoading(true)
        val url = settingsRepository.getLoadUrl()
        webViewController.loadUrl(url)
    }

    private fun reloadCurrent() {
        hideError()
        showLoading(true)
        val current = webViewController.getCurrentUrl()
        if (current.isNullOrBlank() || current == "about:blank") {
            loadConfiguredUrl()
        } else {
            webViewController.reload()
        }
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    private fun applyOrientation(settings: AppSettings) {
        requestedOrientation = if (settings.landscapeEnabled) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    private fun applyFullscreen(enabled: Boolean) {
        WindowCompat.setDecorFitsSystemWindows(window, !enabled)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun showLoading(show: Boolean) {
        binding.loadingOverlay.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showError(message: String) {
        showLoading(false)
        binding.errorMessage.text = message
        binding.errorOverlay.visibility = View.VISIBLE
        binding.btnRetry.requestFocus()
        settingsRepository.saveLastError(message)
    }

    private fun hideError() {
        binding.errorOverlay.visibility = View.GONE
    }

    private fun showBanner(text: String) {
        binding.toastBanner.text = text
        binding.toastBanner.visibility = View.VISIBLE
        handler.removeCallbacks(bannerRunnable)
        handler.postDelayed(bannerRunnable, 2500)
    }

    private val bannerRunnable = Runnable {
        binding.toastBanner.visibility = View.GONE
    }

    // region WebViewController.Callbacks

    override fun onPageStarted(url: String?) {
        runOnUiThread {
            hideError()
            showLoading(true)
        }
    }

    override fun onPageFinished(url: String?) {
        runOnUiThread {
            showLoading(false)
            if (settings.rememberLastPage && !url.isNullOrBlank() && url != "about:blank") {
                settingsRepository.saveLastPageUrl(url)
            }
            binding.webView.requestFocus()
        }
    }

    override fun onProgressChanged(progress: Int) {
        runOnUiThread {
            if (progress >= 90) {
                showLoading(false)
            }
        }
    }

    override fun onReceivedError(description: String) {
        runOnUiThread {
            showError(getString(R.string.check_connection) + "\n\n" + description)
        }
    }

    override fun onExternalBlocked(url: String) {
        runOnUiThread {
            showBanner(getString(R.string.external_link_blocked))
        }
    }

    override fun onFullscreenChanged(isFullscreen: Boolean) {
        runOnUiThread {
            applyFullscreen(settings.fullscreenEnabled || isFullscreen)
        }
    }

    override fun onTitleChanged(title: String?) {
        // no-op
    }

    // endregion

    companion object {
        const val EXTRA_FORCE_RELOAD = "force_reload"
        private const val KEY_WEBVIEW_STATE = "webview_state"
        private const val LONG_BACK_MS = 600L
    }
}
