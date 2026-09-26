package com.webbox.tv.webview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import com.webbox.tv.BuildConfig
import com.webbox.tv.data.AppSettings

class WebViewController(
    private val context: Context,
    private val webView: WebView,
    private val fullscreenContainer: FrameLayout,
    private val callbacks: Callbacks
) {
    interface Callbacks {
        fun onPageStarted(url: String?)
        fun onPageFinished(url: String?)
        fun onProgressChanged(progress: Int)
        fun onReceivedError(description: String)
        fun onExternalBlocked(url: String)
        fun onFullscreenChanged(isFullscreen: Boolean)
        fun onTitleChanged(title: String?)
    }

    private var navigationHandler = NavigationHandler(
        configuredBaseUrl = AppSettings.DEFAULT_URL,
        allowExternalNavigation = false
    )
    private lateinit var tvInputHandler: TvInputHandler
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var originalSystemUiVisibility: Int = 0
    private var allowPopups: Boolean = false
    private var settings: AppSettings = AppSettings()
    private var hasMainFrameError: Boolean = false
    private var chromeClient: WebChromeClient? = null
    private val handler = Handler(Looper.getMainLooper())

    @SuppressLint("SetJavaScriptEnabled")
    fun setup(initialSettings: AppSettings) {
        settings = initialSettings
        navigationHandler.updatePolicy(
            baseUrl = initialSettings.websiteUrl,
            allowExternal = initialSettings.allowExternalNavigation
        )
        allowPopups = initialSettings.allowPopups
        tvInputHandler = TvInputHandler(webView, initialSettings.dpadNavigationEnabled)

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                setAcceptThirdPartyCookies(webView, true)
            }
        }

        webView.setBackgroundColor(Color.BLACK)
        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        webView.isClickable = true
        webView.scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
        webView.overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = false
            displayZoomControls = false
            setSupportZoom(false)
            cacheMode = WebSettings.LOAD_DEFAULT
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = allowPopups
            setSupportMultipleWindows(true)
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            userAgentString = desktopUserAgent(userAgentString)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                safeBrowsingEnabled = true
            }
        }

        webView.webViewClient = createWebViewClient()
        chromeClient = createWebChromeClient()
        webView.webChromeClient = chromeClient
    }

    fun applySettings(newSettings: AppSettings) {
        settings = newSettings
        navigationHandler.updatePolicy(
            baseUrl = newSettings.websiteUrl,
            allowExternal = newSettings.allowExternalNavigation
        )
        allowPopups = newSettings.allowPopups
        tvInputHandler.setEnhancementEnabled(newSettings.dpadNavigationEnabled)
        webView.settings.javaScriptCanOpenWindowsAutomatically = allowPopups
        webView.settings.setSupportMultipleWindows(true)
    }

    fun loadUrl(url: String) {
        hasMainFrameError = false
        if (BuildConfig.DEBUG) Log.d(TAG, "loadUrl: $url")
        webView.loadUrl(url)
    }

    fun reload() {
        hasMainFrameError = false
        webView.reload()
    }

    fun canGoBack(): Boolean = webView.canGoBack()

    fun goBack() {
        if (webView.canGoBack()) webView.goBack()
    }

    fun isVideoFullscreen(): Boolean = customView != null

    fun exitFullscreen(): Boolean {
        if (customView == null) return false
        chromeClient?.onHideCustomView()
        return true
    }

    fun handleKeyEvent(event: android.view.KeyEvent): Boolean {
        return tvInputHandler.handleKeyEvent(event)
    }

    fun saveState(outState: Bundle) {
        webView.saveState(outState)
    }

    fun restoreState(inState: Bundle): Boolean {
        return webView.restoreState(inState) != null
    }

    fun onPause() {
        webView.onPause()
        CookieManager.getInstance().flush()
    }

    fun onResume() {
        webView.onResume()
    }

    fun destroy() {
        exitFullscreen()
        handler.removeCallbacksAndMessages(null)
        webView.stopLoading()
        webView.loadUrl("about:blank")
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.removeAllViews()
        webView.destroy()
    }

    fun clearWebsiteData(onDone: () -> Unit) {
        CookieManager.getInstance().removeAllCookies {
            CookieManager.getInstance().flush()
            webView.clearCache(true)
            webView.clearHistory()
            webView.clearFormData()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                android.webkit.WebStorage.getInstance().deleteAllData()
            }
            onDone()
        }
    }

    fun getCurrentUrl(): String? = webView.url

    fun getWebViewVersion(): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WebView.getCurrentWebViewPackage()?.versionName ?: "unknown"
            } else {
                webView.settings.userAgentString
            }
        } catch (_: Exception) {
            "unknown"
        }
    }

    private fun createWebViewClient(): WebViewClient {
        return object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                if (request.isForMainFrame == false) return false
                return handleNavigation(url)
            }

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                return handleNavigation(url)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                hasMainFrameError = false
                if (BuildConfig.DEBUG) Log.d(TAG, "onPageStarted: $url")
                callbacks.onPageStarted(url)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (BuildConfig.DEBUG) Log.d(TAG, "onPageFinished: $url")
                if (settings.dpadNavigationEnabled) {
                    tvInputHandler.injectFocusScript()
                }
                injectPopupGuard()
                callbacks.onPageFinished(url)
            }

            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)
                if (settings.dpadNavigationEnabled) {
                    tvInputHandler.injectFocusScript()
                }
                injectPopupGuard()
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    hasMainFrameError = true
                    val desc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        error?.description?.toString() ?: "Unknown error"
                    } else {
                        "Page load error"
                    }
                    if (BuildConfig.DEBUG) Log.e(TAG, "onReceivedError: $desc")
                    callbacks.onReceivedError(desc)
                }
            }

            @Deprecated("Deprecated in Java")
            @Suppress("DEPRECATION")
            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                hasMainFrameError = true
                callbacks.onReceivedError(description ?: "Page load error")
            }

            override fun onReceivedSslError(
                view: WebView?,
                handler: android.webkit.SslErrorHandler?,
                error: android.net.http.SslError?
            ) {
                // Fail safely — do not bypass SSL errors
                if (BuildConfig.DEBUG) Log.e(TAG, "SSL error: ${error?.toString()}")
                handler?.cancel()
                hasMainFrameError = true
                callbacks.onReceivedError("SSL certificate error. Connection is not secure.")
            }
        }
    }

    private fun handleNavigation(url: String?): Boolean {
        return when (val decision = navigationHandler.shouldOverrideUrlLoading(url)) {
            is NavigationHandler.Decision.Allow -> false
            is NavigationHandler.Decision.Block -> {
                if (url != null) callbacks.onExternalBlocked(url)
                true
            }
        }
    }

    private fun createWebChromeClient(): WebChromeClient {
        return object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                callbacks.onProgressChanged(newProgress)
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                callbacks.onTitleChanged(title)
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (customView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
                if (BuildConfig.DEBUG) Log.d(TAG, "Enter video fullscreen")
                customView = view
                customViewCallback = callback
                fullscreenContainer.visibility = View.VISIBLE
                fullscreenContainer.addView(
                    view,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                view?.isFocusable = true
                view?.isFocusableInTouchMode = true
                view?.requestFocus()
                webView.visibility = View.GONE
                callbacks.onFullscreenChanged(true)
            }

            override fun onHideCustomView() {
                if (customView == null) return
                if (BuildConfig.DEBUG) Log.d(TAG, "Exit video fullscreen")
                fullscreenContainer.removeView(customView)
                fullscreenContainer.visibility = View.GONE
                customView = null
                customViewCallback?.onCustomViewHidden()
                customViewCallback = null
                webView.visibility = View.VISIBLE
                webView.requestFocus()
                callbacks.onFullscreenChanged(false)
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message?
            ): Boolean {
                if (!navigationHandler.shouldOpenPopup(allowPopups)) {
                    if (BuildConfig.DEBUG) Log.d(TAG, "Popup blocked (Block Popups is ON)")
                    return swallowPopup(resultMsg)
                }
                val newWebView = WebView(context)
                newWebView.webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        if (!url.isNullOrBlank() && url != "about:blank") {
                            routeToMain(url)
                        }
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val url = request?.url?.toString() ?: return true
                        if (request.isForMainFrame == false) return false
                        routeToMain(url)
                        return true
                    }

                    @Deprecated("Deprecated in Java")
                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                        if (url != null) routeToMain(url)
                        return true
                    }
                }
                val transport = resultMsg?.obj as? WebView.WebViewTransport
                transport?.webView = newWebView
                resultMsg?.sendToTarget()
                return true
            }

            override fun onPermissionRequest(request: android.webkit.PermissionRequest?) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    request?.grant(request.resources)
                } else {
                    super.onPermissionRequest(request)
                }
            }
        }
    }

    private fun routeToMain(url: String) {
        when (navigationHandler.shouldOverrideUrlLoading(url)) {
            is NavigationHandler.Decision.Allow -> webView.loadUrl(url)
            is NavigationHandler.Decision.Block -> callbacks.onExternalBlocked(url)
        }
    }

    /**
     * Completes a popup request with a detached WebView that is never attached to the view
     * hierarchy, so the popup cannot render on screen and cannot fall back to navigating the
     * main WebView. Returning false from onCreateWindow is not enough on all WebView builds —
     * the popup can still be shown or open in the current page.
     */
    private fun swallowPopup(resultMsg: android.os.Message?): Boolean {
        val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
        val discard = WebView(context)
        discard.settings.apply {
            javaScriptEnabled = false
            javaScriptCanOpenWindowsAutomatically = false
        }
        discard.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return true
            }

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                return true
            }
        }
        transport.webView = discard
        resultMsg.sendToTarget()
        handler.postDelayed({ destroyDiscard(discard) }, POPUP_SWALLOW_MS)
        return true
    }

    private fun destroyDiscard(view: WebView) {
        try {
            view.stopLoading()
            view.loadUrl("about:blank")
            (view.parent as? ViewGroup)?.removeView(view)
            view.destroy()
        } catch (_: Exception) {
            // Best effort cleanup
        }
    }

    private fun injectPopupGuard() {
        if (allowPopups) return
        webView.evaluateJavascript(POPUP_GUARD_JS, null)
        webView.evaluateJavascript(AD_OVERLAY_GUARD_JS, null)
    }

    private fun desktopUserAgent(defaultUa: String): String {
        val chrome = Regex("Chrome/[\\d.]+").find(defaultUa)?.value ?: "Chrome/120.0.0.0"
        return "Mozilla/5.0 (Linux; Android 13; SHIELD Android TV) AppleWebKit/537.36 (KHTML, like Gecko) $chrome Safari/537.36"
    }

    companion object {
        private const val TAG = "WebBoxWebView"
        private const val POPUP_SWALLOW_MS = 3000L

        private val POPUP_GUARD_JS = """
            (function() {
              if (window.__webboxPopupGuard) return;
              window.__webboxPopupGuard = true;
              // Neutralize scripted popups (window.open) while blocking is enabled.
              try {
                window.open = function() { return null; };
              } catch (e) {}
              // Send target="_blank" links through normal navigation so the
              // navigation handler can block external/redirect targets.
              function hardenTargets() {
                try {
                  var links = document.querySelectorAll('a[target="_blank"],area[target="_blank"]');
                  for (var i = 0; i < links.length; i++) {
                    links[i].setAttribute('target', '_self');
                  }
                } catch (e) {}
              }
              hardenTargets();
              try {
                var obs = new MutationObserver(hardenTargets);
                obs.observe(document.documentElement, {childList: true, subtree: true});
              } catch (e) {}
            })();
        """.trimIndent()

        /**
         * Removes full-screen interstitial ad overlays injected into the page (typically a high
         * z-index fixed/absolute element covering the viewport) and restores page scrolling while
         * they lock it. Runs continuously because some sites re-inject the overlay on a timer.
         * Player elements (video / known player iframes) are never touched.
         */
        private val AD_OVERLAY_GUARD_JS = """
            (function() {
              if (window.__webboxAdGuard) return;
              window.__webboxAdGuard = true;

              var HARD_Z = 10000;
              var RATIO = 0.7;
              var MAX_DEPTH = 3;
              var SCAN_MS = 800;

              function vw() { return Math.max(document.documentElement.clientWidth || 0, window.innerWidth || 0); }
              function vh() { return Math.max(document.documentElement.clientHeight || 0, window.innerHeight || 0); }

              function coversViewport(el) {
                var r = el.getBoundingClientRect();
                var w = vw(), h = vh();
                if (w > 0 && r.width < w * RATIO) return false;
                if (h > 0 && r.height < h * RATIO) return false;
                if (h > 0 && r.top > h * 0.15) return false;
                return true;
              }

              function classNameOf(el) {
                var c = el.className;
                if (c && typeof c === 'object' && typeof c.baseVal === 'string') c = c.baseVal;
                return (el.id || '') + ' ' + (typeof c === 'string' ? c : '');
              }

              function hasAdName(el) {
                return /(^|[^a-z])(ad|ads|advert|advertisement|popup|pop-up|interstitial|overlay|promo|sponsor|banner|preroll|enabler)([^a-z]|$)/i
                  .test(classNameOf(el));
              }

              function hasPlayer(el) {
                try {
                  if (el.tagName === 'VIDEO') return true;
                  if (el.querySelector && el.querySelector('video')) return true;
                  var frames = el.querySelectorAll ? el.querySelectorAll('iframe[src]') : [];
                  for (var i = 0; i < frames.length; i++) {
                    var src = frames[i].getAttribute('src') || '';
                    if (!src) continue;
                    try {
                      var u = new URL(src, location.href);
                      if (u.host === location.host || u.host.endsWith('.' + location.host)) return true;
                    } catch (e) {}
                    if (/vidsrc|filemoon|streamtape|streamwish|mixdrop|megacloud|rabbitstream|mcloud|vidplay|autoembed|2embed|smashy|hydrax|embed|player|youtube\.com|youtu\.be|vimeo|dailymotion|jwplayer|ok\.ru/i.test(src)) return true;
                  }
                } catch (e) {}
                return false;
              }

              function isInterstitial(el) {
                if (!el || el.nodeType !== 1) return false;
                var tag = el.tagName;
                if (tag === 'HTML' || tag === 'BODY' || tag === 'VIDEO' || tag === 'SCRIPT' || tag === 'STYLE') return false;
                if (el.getAttribute('data-webbox-adguard')) return false;
                if (hasPlayer(el)) return false;
                var style;
                try { style = window.getComputedStyle(el); } catch (e) { return false; }
                if (!style) return false;
                if (style.display === 'none' || style.visibility === 'hidden') return false;
                if (style.position !== 'fixed' && style.position !== 'absolute') return false;
                if (!coversViewport(el)) return false;
                var z = parseInt(style.zIndex, 10);
                if (!isNaN(z) && z >= HARD_Z) return true;
                return hasAdName(el);
              }

              function unlockScroll() {
                try {
                  var b = document.body, h = document.documentElement;
                  if (b && window.getComputedStyle(b).overflow === 'hidden') {
                    b.style.setProperty('overflow', 'auto', 'important');
                    b.style.setProperty('position', 'static', 'important');
                  }
                  if (h && window.getComputedStyle(h).overflow === 'hidden') {
                    h.style.setProperty('overflow', 'auto', 'important');
                  }
                } catch (e) {}
              }

              function removeOverlay(el) {
                try {
                  el.setAttribute('data-webbox-adguard', 'removed');
                  el.style.setProperty('display', 'none', 'important');
                  el.style.setProperty('visibility', 'hidden', 'important');
                  el.style.setProperty('pointer-events', 'none', 'important');
                  if (el.parentNode) el.parentNode.removeChild(el);
                } catch (e) {}
              }

              function walk(el, depth) {
                if (!el || depth > MAX_DEPTH) return;
                var children = el.children;
                if (!children) return;
                for (var i = 0; i < children.length; i++) {
                  var child = children[i];
                  if (isInterstitial(child)) {
                    removeOverlay(child);
                    continue;
                  }
                  walk(child, depth + 1);
                }
              }

              function scan() {
                unlockScroll();
                walk(document.body, 0);
                walk(document.documentElement, 0);
              }

              scan();
              var scanTimer = null;
              function schedule() {
                if (scanTimer) return;
                scanTimer = setTimeout(function() { scanTimer = null; scan(); }, 300);
              }
              setInterval(scan, SCAN_MS);
              try {
                var observer = new MutationObserver(schedule);
                observer.observe(document.documentElement, {childList: true, subtree: true});
              } catch (e) {}
            })();
        """.trimIndent()
    }
}
