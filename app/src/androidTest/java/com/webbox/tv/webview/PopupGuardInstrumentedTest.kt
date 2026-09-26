package com.webbox.tv.webview

import android.webkit.WebView
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.webbox.tv.data.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * On-device tests for the popup/redirect guard, driven through the real
 * [WebViewController] and the platform WebView.
 */
@RunWith(AndroidJUnit4::class)
class PopupGuardInstrumentedTest {

    private val noopCallbacks = object : WebViewController.Callbacks {
        override fun onPageStarted(url: String?) {}
        override fun onPageFinished(url: String?) {}
        override fun onProgressChanged(progress: Int) {}
        override fun onReceivedError(description: String) {}
        override fun onExternalBlocked(url: String) {}
        override fun onFullscreenChanged(isFullscreen: Boolean) {}
        override fun onTitleChanged(title: String?) {}
    }

    private val pageHtml = """
        <!doctype html><html><body>
          <a id="ext" href="https://example.com/ad" target="_blank">ad</a>
          <a id="int" href="https://example.com/local" target="_blank">local</a>
        </body></html>
    """.trimIndent()

    private val interstitialHtml = """
        <!doctype html><html><body>
          <div id="content">site content</div>
          <div id="ad-overlay"
               style="position:fixed;top:0;left:0;width:100%;height:100%;z-index:999999;background:#000;">
            <a href="https://example.com/ad">close ad</a>
          </div>
        </body></html>
    """.trimIndent()

    @Test
    fun fullScreenInterstitialRemovedWhenBlocking() {
        val controller = buildController(allowPopups = false, html = interstitialHtml)
        awaitGuard(controller.webView, expected = true)

        assertEquals(
            "full-screen interstitial overlay must be removed",
            "removed",
            eval(controller.webView, "document.getElementById('ad-overlay') === null ? 'removed' : 'present'")
        )
        assertEquals(
            "page content must be preserved",
            "present",
            eval(controller.webView, "document.getElementById('content') === null ? 'missing' : 'present'")
        )

        controller.destroy()
    }

    @Test
    fun recurringInterstitialRemovedWhenBlocking() {
        val controller = buildController(allowPopups = false, html = interstitialHtml)
        awaitGuard(controller.webView, expected = true)
        Thread.sleep(500)

        eval(
            controller.webView,
            "(function(){var d=document.createElement('div');d.id='ad-overlay-2';" +
                "d.setAttribute('style','position:fixed;top:0;left:0;width:100%;height:100%;z-index:999999;background:#111');" +
                "document.body.appendChild(d);return 'ok';})()"
        )

        val deadline = System.currentTimeMillis() + 5_000
        var removed = false
        while (System.currentTimeMillis() < deadline) {
            if (eval(controller.webView, "document.getElementById('ad-overlay-2') === null ? 'removed' : 'present'") == "removed") {
                removed = true
                break
            }
            Thread.sleep(200)
        }
        assertTrue("re-injected interstitial must be removed", removed)

        controller.destroy()
    }

    @Test
    fun interstitialKeptWhenPopupsAllowed() {
        val controller = buildController(allowPopups = true, html = interstitialHtml)
        Thread.sleep(2000)

        assertEquals(
            "interstitial must not be removed when popups are allowed",
            "present",
            eval(controller.webView, "document.getElementById('ad-overlay') === null ? 'removed' : 'present'")
        )

        controller.destroy()
    }

    @Test
    fun guardRunsWhenPopupsBlocked() {
        val controller = buildController(allowPopups = false)

        awaitGuard(controller.webView, expected = true)

        assertEquals(
            "window.open should be neutralized",
            "true",
            eval(controller.webView, "String(window.__webboxPopupGuard === true)")
        )
        assertEquals(
            "target=_blank links should be rewritten to _self",
            "0",
            eval(controller.webView, "document.querySelectorAll('a[target=_blank]').length")
        )
        assertEquals(
            "both anchors should now target _self",
            "2",
            eval(controller.webView, "document.querySelectorAll('a[target=_self]').length")
        )

        controller.destroy()
    }

    @Test
    fun guardNotInjectedWhenPopupsAllowed() {
        val controller = buildController(allowPopups = true)

        // Give the page time to finish and any (non) injection to occur.
        Thread.sleep(2000)

        assertEquals(
            "guard script must not run when popups are allowed",
            "false",
            eval(controller.webView, "String(window.__webboxPopupGuard === true)")
        )
        assertEquals(
            "target=_blank links must be left intact when popups are allowed",
            "2",
            eval(controller.webView, "document.querySelectorAll('a[target=_blank]').length")
        )

        controller.destroy()
    }

    @Test
    fun windowOpenDoesNotNavigateMainWebView() {
        val controller = buildController(allowPopups = false)
        awaitGuard(controller.webView, expected = true)

        val before = controller.getCurrentUrl()
        eval(controller.webView, "window.open('https://example.com/ad','_blank'); 'ok'")
        Thread.sleep(1000)
        val after = controller.getCurrentUrl()

        assertFalse("main WebView must not be navigated by a popup", after?.startsWith("https://example.com/ad") == true)
        assertEquals("main WebView URL should not change on popup attempt", before, after)

        controller.destroy()
    }

    private class Controller(val viewController: WebViewController, val webView: WebView) {
        fun destroy() = viewController.destroy()
        fun getCurrentUrl(): String? = viewController.getCurrentUrl()
    }

    private fun buildController(allowPopups: Boolean, html: String = pageHtml): Controller {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val holder = AtomicReference<WebViewController>()
        val webViewRef = AtomicReference<WebView>()

        instrumentation.runOnMainSync {
            val container = FrameLayout(context)
            val webView = WebView(context)
            container.addView(webView)
            val controller = WebViewController(context, webView, container, noopCallbacks)
            controller.setup(
                AppSettings(
                    websiteUrl = "https://example.com/",
                    allowPopups = allowPopups,
                    dpadNavigationEnabled = false
                )
            )
            holder.set(controller)
            webViewRef.set(webView)
            webView.loadDataWithBaseURL(
                "https://example.com/",
                html,
                "text/html",
                "utf-8",
                null
            )
        }

        return Controller(holder.get(), webViewRef.get())
    }

    private fun awaitGuard(webView: WebView, expected: Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            val value = eval(webView, "String(window.__webboxPopupGuard === true)")
            if (value == expected.toString()) return
            Thread.sleep(200)
        }
        throw AssertionError("Timed out waiting for popup guard == $expected")
    }

    private fun eval(webView: WebView, script: String): String {
        val latch = CountDownLatch(1)
        val result = AtomicReference<String>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            webView.evaluateJavascript(script) { value ->
                result.set(value)
                latch.countDown()
            }
        }
        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw AssertionError("evaluateJavascript timed out: $script")
        }
        // Strip surrounding JSON string quotes that evaluateJavascript may add.
        return result.get()?.removeSurrounding("\"") ?: "null"
    }
}
