package com.webbox.tv.webview

import android.util.Log
import android.view.KeyEvent
import android.webkit.WebView
import com.webbox.tv.BuildConfig

class TvInputHandler(
    private val webView: WebView,
    private var dpadEnhancementEnabled: Boolean
) {
    fun setEnhancementEnabled(enabled: Boolean) {
        dpadEnhancementEnabled = enabled
    }

    fun handleKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false

        if (BuildConfig.DEBUG && isDpad(event.keyCode)) {
            Log.d(TAG, "D-pad key=${event.keyCode} repeat=${event.repeatCount}")
        }

        when (event.keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                injectMediaToggle()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                injectMediaSeek(10)
                return true
            }
            KeyEvent.KEYCODE_MEDIA_REWIND,
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                injectMediaSeek(-10)
                return true
            }
        }

        if (!dpadEnhancementEnabled) return false

        return when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                injectMove("up"); true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                injectMove("down"); true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                injectMove("left"); true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                injectMove("right"); true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                injectActivate(); true
            }
            else -> false
        }
    }

    fun injectFocusScript() {
        if (!dpadEnhancementEnabled) return
        webView.evaluateJavascript(FOCUS_CSS_JS, null)
        webView.evaluateJavascript(FOCUS_ENGINE_JS, null)
    }

    private fun injectMove(direction: String) {
        webView.evaluateJavascript(
            "window.__webboxTvNav && window.__webboxTvNav.move('$direction');",
            null
        )
    }

    private fun injectActivate() {
        webView.evaluateJavascript(
            "window.__webboxTvNav && window.__webboxTvNav.activate();",
            null
        )
    }

    private fun injectMediaToggle() {
        webView.evaluateJavascript(
            "(function(){var v=document.querySelector('video');if(!v)return;if(v.paused){v.play();}else{v.pause();}})();",
            null
        )
    }

    private fun injectMediaSeek(seconds: Int) {
        webView.evaluateJavascript(
            "(function(){var v=document.querySelector('video');if(!v)return;try{v.currentTime=Math.max(0,v.currentTime+($seconds));}catch(e){}})();",
            null
        )
    }

    private fun isDpad(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_DPAD_UP ||
            keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
            keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
            keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
            keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
            keyCode == KeyEvent.KEYCODE_ENTER
    }

    companion object {
        private const val TAG = "WebBoxInput"

        private val FOCUS_CSS_JS = """
            (function() {
              if (document.getElementById('webbox-tv-focus-style')) return;
              var style = document.createElement('style');
              style.id = 'webbox-tv-focus-style';
              style.textContent = '.tv-focused{outline:3px solid #FFD54F !important;outline-offset:4px !important;box-shadow:0 0 0 6px rgba(255,213,79,0.35) !important;border-radius:8px;}';
              (document.head || document.documentElement).appendChild(style);
            })();
        """.trimIndent()

        private val FOCUS_ENGINE_JS = """
            (function() {
              if (window.__webboxTvNav) {
                window.__webboxTvNav.ensure();
                return;
              }
              var current = null;
              var SELECTOR = 'a[href],button,input,select,textarea,video,iframe,summary,[tabindex]:not([tabindex="-1"]),[role="button"],[role="link"],[role="tab"],[role="menuitem"],[role="option"],[onclick]';

              function isVisible(el) {
                if (!el) return false;
                if (el.disabled) return false;
                if (el.getAttribute('aria-hidden') === 'true') return false;
                var style = window.getComputedStyle(el);
                if (!style) return false;
                if (style.display === 'none' || style.visibility === 'hidden' || style.pointerEvents === 'none') return false;
                if (parseFloat(style.opacity) === 0) return false;
                var rect = el.getBoundingClientRect();
                return rect.width > 8 && rect.height > 8;
              }

              function inViewport(el, slack) {
                slack = slack || 0;
                var r = el.getBoundingClientRect();
                return r.bottom > -slack && r.right > -slack &&
                  r.top < (window.innerHeight + slack) && r.left < (window.innerWidth + slack);
              }

              function candidates(viewportOnly) {
                var nodes = Array.prototype.slice.call(document.querySelectorAll(SELECTOR));
                var out = [];
                for (var i = 0; i < nodes.length; i++) {
                  var el = nodes[i];
                  if (!isVisible(el)) continue;
                  if (viewportOnly && !inViewport(el, 120)) continue;
                  out.push(el);
                }
                return out;
              }

              function clearFocus() {
                if (current) current.classList.remove('tv-focused');
              }

              function scrollParents(el) {
                var parent = el.parentElement;
                while (parent && parent !== document.body && parent !== document.documentElement) {
                  var ox = parent.scrollWidth > parent.clientWidth + 8;
                  var oy = parent.scrollHeight > parent.clientHeight + 8;
                  if (ox || oy) {
                    var er = el.getBoundingClientRect();
                    var pr = parent.getBoundingClientRect();
                    var pad = 28;
                    if (er.right > pr.right - pad) parent.scrollLeft += (er.right - pr.right + pad);
                    if (er.left < pr.left + pad) parent.scrollLeft -= (pr.left - er.left + pad);
                    if (er.bottom > pr.bottom - pad) parent.scrollTop += (er.bottom - pr.bottom + pad);
                    if (er.top < pr.top + pad) parent.scrollTop -= (pr.top - er.top + pad);
                  }
                  parent = parent.parentElement;
                }
                try {
                  el.scrollIntoView({block:'nearest', inline:'nearest', behavior:'smooth'});
                } catch (e) {
                  try { el.scrollIntoView(false); } catch (e2) {}
                }
              }

              function setFocus(el) {
                if (!el) return;
                clearFocus();
                current = el;
                current.classList.add('tv-focused');
                try { current.focus({preventScroll:true}); } catch (e) { try { current.focus(); } catch (e2) {} }
                scrollParents(current);
              }

              function center(el) {
                var r = el.getBoundingClientRect();
                return { x: r.left + r.width / 2, y: r.top + r.height / 2, r: r };
              }

              function preferredStart() {
                var list = candidates(true);
                if (!list.length) list = candidates(false);
                if (!list.length) return null;
                for (var i = 0; i < list.length; i++) {
                  var t = (list[i].textContent || '').replace(/\s+/g, ' ').trim().toLowerCase();
                  if (t === 'play') return list[i];
                }
                var best = list[0];
                var bestArea = 0;
                for (var j = 0; j < list.length; j++) {
                  var r = list[j].getBoundingClientRect();
                  if (r.top < 90) continue;
                  var area = r.width * r.height;
                  if (area > bestArea) {
                    bestArea = area;
                    best = list[j];
                  }
                }
                return best;
              }

              function pick(dir) {
                var list = candidates(true);
                if (!list.length) list = candidates(false);
                if (!list.length) return null;
                if (!current || list.indexOf(current) < 0 || !document.contains(current)) {
                  return preferredStart();
                }
                var c = center(current);
                var best = null;
                var bestScore = Infinity;
                for (var i = 0; i < list.length; i++) {
                  var el = list[i];
                  if (el === current) continue;
                  var p = center(el);
                  var dx = p.x - c.x;
                  var dy = p.y - c.y;
                  var ok = false;
                  if (dir === 'up' && dy < -10) ok = true;
                  if (dir === 'down' && dy > 10) ok = true;
                  if (dir === 'left' && dx < -10) ok = true;
                  if (dir === 'right' && dx > 10) ok = true;
                  if (!ok) continue;
                  var absDx = dx < 0 ? -dx : dx;
                  var absDy = dy < 0 ? -dy : dy;
                  if (dir === 'up' || dir === 'down') {
                    if (absDx > absDy * 3.2 && absDx > 140) continue;
                  } else {
                    if (absDy > absDx * 3.2 && absDy > 140) continue;
                  }
                  var dist = Math.sqrt(dx * dx + dy * dy);
                  var axis = (dir === 'up' || dir === 'down') ? absDx : absDy;
                  var overlapBonus = 0;
                  var cr = c.r;
                  var er = p.r;
                  if (dir === 'left' || dir === 'right') {
                    var vOverlap = Math.min(cr.bottom, er.bottom) - Math.max(cr.top, er.top);
                    if (vOverlap > 8) overlapBonus = -vOverlap * 0.6;
                  } else {
                    var hOverlap = Math.min(cr.right, er.right) - Math.max(cr.left, er.left);
                    if (hOverlap > 8) overlapBonus = -hOverlap * 0.6;
                  }
                  var score = dist + axis * 1.35 + overlapBonus;
                  if (score < bestScore) {
                    bestScore = score;
                    best = el;
                  }
                }
                if (!best) {
                  var amountY = Math.floor(window.innerHeight * 0.45);
                  var amountX = Math.floor(window.innerWidth * 0.35);
                  if (dir === 'down') window.scrollBy(0, amountY);
                  if (dir === 'up') window.scrollBy(0, -amountY);
                  if (dir === 'right') window.scrollBy(amountX, 0);
                  if (dir === 'left') window.scrollBy(-amountX, 0);
                  list = candidates(true);
                  for (var k = 0; k < list.length; k++) {
                    if (list[k] !== current && isVisible(list[k])) {
                      var p2 = center(list[k]);
                      var dx2 = p2.x - c.x;
                      var dy2 = p2.y - c.y;
                      if (dir === 'down' && dy2 > 10) return list[k];
                      if (dir === 'up' && dy2 < -10) return list[k];
                      if (dir === 'right' && dx2 > 10) return list[k];
                      if (dir === 'left' && dx2 < -10) return list[k];
                    }
                  }
                }
                return best;
              }

              function hookHistory() {
                if (window.__webboxHistoryHooked) return;
                window.__webboxHistoryHooked = true;
                function wrap(type) {
                  var orig = history[type];
                  history[type] = function() {
                    var ret = orig.apply(this, arguments);
                    setTimeout(function() {
                      if (window.__webboxTvNav) window.__webboxTvNav.ensure(true);
                    }, 280);
                    return ret;
                  };
                }
                wrap('pushState');
                wrap('replaceState');
                window.addEventListener('popstate', function() {
                  setTimeout(function() {
                    if (window.__webboxTvNav) window.__webboxTvNav.ensure(true);
                  }, 280);
                });
              }

              window.__webboxTvNav = {
                move: function(dir) {
                  var next = pick(dir);
                  if (next) setFocus(next);
                },
                activate: function() {
                  if (!current || !document.contains(current) || !isVisible(current)) {
                    var first = preferredStart();
                    if (first) setFocus(first);
                    return;
                  }
                  var tag = current.tagName;
                  if (tag === 'VIDEO') {
                    if (current.paused) current.play(); else current.pause();
                    return;
                  }
                  if (tag === 'IFRAME') {
                    try { current.contentWindow && current.contentWindow.focus(); } catch (e) {}
                    current.focus();
                    return;
                  }
                  if (typeof current.click === 'function') current.click();
                  else {
                    var evt = new MouseEvent('click', {bubbles:true, cancelable:true, view:window});
                    current.dispatchEvent(evt);
                  }
                },
                ensure: function(reset) {
                  if (reset) {
                    clearFocus();
                    current = null;
                  }
                  if (!current || !document.contains(current) || !isVisible(current)) {
                    var first = preferredStart();
                    if (first) setFocus(first);
                  }
                }
              };

              hookHistory();
              var obsTimer = null;
              try {
                var obs = new MutationObserver(function() {
                  if (obsTimer) clearTimeout(obsTimer);
                  obsTimer = setTimeout(function() {
                    if (current && !document.contains(current)) {
                      current = null;
                      window.__webboxTvNav.ensure();
                    }
                  }, 400);
                });
                obs.observe(document.documentElement, {childList:true, subtree:true});
              } catch (e) {}
              setTimeout(function() { window.__webboxTvNav.ensure(); }, 250);
            })();
        """.trimIndent()
    }
}
