package com.webbox.tv.ui

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.webbox.tv.R
import com.webbox.tv.WebBoxApp
import com.webbox.tv.data.AppSettings
import com.webbox.tv.data.SettingsRepository
import com.webbox.tv.util.NetworkUtils
import com.webbox.tv.util.UrlValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var settings: AppSettings

    private lateinit var urlEditText: TextInputEditText
    private lateinit var urlErrorText: TextView
    private lateinit var btnTestWebsite: MaterialButton
    private lateinit var btnSaveLoad: MaterialButton
    private lateinit var btnReload: MaterialButton
    private lateinit var btnClearData: MaterialButton
    private lateinit var btnDiagnostics: MaterialButton
    private lateinit var btnReset: MaterialButton
    private lateinit var btnCancel: MaterialButton

    private lateinit var rowFullscreen: LinearLayout
    private lateinit var rowLandscape: LinearLayout
    private lateinit var rowDpad: LinearLayout
    private lateinit var rowBlockExternal: LinearLayout
    private lateinit var rowBlockPopups: LinearLayout
    private lateinit var rowRememberLast: LinearLayout

    private var fullscreenEnabled = true
    private var landscapeEnabled = true
    private var dpadEnabled = true
    private var blockExternal = true
    private var blockPopups = true
    private var rememberLast = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        settingsRepository = (application as WebBoxApp).settingsRepository
        settings = settingsRepository.getSettings()

        bindViews()
        loadFromSettings(settings)
        setupListeners()

        urlEditText.showSoftInputOnFocus = false
        btnTestWebsite.requestFocus()
    }

    private fun bindViews() {
        urlEditText = findViewById(R.id.urlEditText)
        urlErrorText = findViewById(R.id.urlErrorText)
        btnTestWebsite = findViewById(R.id.btnTestWebsite)
        btnSaveLoad = findViewById(R.id.btnSaveLoad)
        btnReload = findViewById(R.id.btnReload)
        btnClearData = findViewById(R.id.btnClearData)
        btnDiagnostics = findViewById(R.id.btnDiagnostics)
        btnReset = findViewById(R.id.btnReset)
        btnCancel = findViewById(R.id.btnCancel)

        rowFullscreen = findViewById(R.id.rowFullscreen)
        rowLandscape = findViewById(R.id.rowLandscape)
        rowDpad = findViewById(R.id.rowDpad)
        rowBlockExternal = findViewById(R.id.rowBlockExternal)
        rowBlockPopups = findViewById(R.id.rowBlockPopups)
        rowRememberLast = findViewById(R.id.rowRememberLast)
    }

    private fun loadFromSettings(s: AppSettings) {
        urlEditText.setText(s.websiteUrl)
        fullscreenEnabled = s.fullscreenEnabled
        landscapeEnabled = s.landscapeEnabled
        dpadEnabled = s.dpadNavigationEnabled
        blockExternal = !s.allowExternalNavigation
        blockPopups = !s.allowPopups
        rememberLast = s.rememberLastPage

        bindSwitchRow(rowFullscreen, getString(R.string.fullscreen), fullscreenEnabled) {
            fullscreenEnabled = it
        }
        bindSwitchRow(rowLandscape, getString(R.string.landscape), landscapeEnabled) {
            landscapeEnabled = it
        }
        bindSwitchRow(rowDpad, getString(R.string.tv_navigation), dpadEnabled) {
            dpadEnabled = it
        }
        bindSwitchRow(rowBlockExternal, getString(R.string.block_external_links), blockExternal) {
            blockExternal = it
        }
        bindSwitchRow(rowBlockPopups, getString(R.string.block_popups), blockPopups) {
            blockPopups = it
        }
        bindSwitchRow(rowRememberLast, getString(R.string.remember_last_page), rememberLast) {
            rememberLast = it
        }
    }

    private fun bindSwitchRow(
        row: LinearLayout,
        label: String,
        initial: Boolean,
        onToggle: (Boolean) -> Unit
    ) {
        val labelView = row.findViewById<TextView>(R.id.settingLabel)
        val valueView = row.findViewById<TextView>(R.id.settingValue)
        labelView.text = label
        fun render(value: Boolean) {
            valueView.text = if (value) getString(R.string.on) else getString(R.string.off)
            valueView.setTextColor(
                ContextCompat.getColor(
                    this@SettingsActivity,
                    if (value) R.color.webbox_success else R.color.webbox_text_secondary
                )
            )
        }
        var current = initial
        render(current)
        row.isClickable = true
        row.isFocusable = true
        row.isFocusableInTouchMode = true
        row.setOnClickListener {
            current = !current
            render(current)
            onToggle(current)
        }
        row.setOnKeyListener { _, keyCode, event ->
            if (event.action == android.view.KeyEvent.ACTION_DOWN &&
                (keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                    keyCode == android.view.KeyEvent.KEYCODE_ENTER)
            ) {
                row.performClick()
                true
            } else {
                false
            }
        }
    }

    private fun setupListeners() {
        urlEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard()
                btnTestWebsite.requestFocus()
                true
            } else false
        }
        urlEditText.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN &&
                (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)
            ) {
                urlEditText.showSoftInputOnFocus = true
                urlEditText.requestFocus()
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(urlEditText, InputMethodManager.SHOW_IMPLICIT)
                true
            } else {
                false
            }
        }

        btnTestWebsite.setOnClickListener { testWebsite() }
        btnSaveLoad.setOnClickListener { saveAndLoad() }
        btnReload.setOnClickListener {
            setResult(RESULT_OK, Intent().putExtra(MainActivity.EXTRA_FORCE_RELOAD, true))
            // Signal main to reload via flag on next resume
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(MainActivity.EXTRA_FORCE_RELOAD, true)
            )
            finish()
        }
        btnClearData.setOnClickListener { confirmClearData() }
        btnDiagnostics.setOnClickListener {
            startActivity(Intent(this, DiagnosticsActivity::class.java))
        }
        btnReset.setOnClickListener { confirmReset() }
        btnCancel.setOnClickListener { finish() }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(urlEditText.windowToken, 0)
        urlEditText.showSoftInputOnFocus = false
    }

    private fun currentUrlInput(): String = urlEditText.text?.toString().orEmpty()

    private fun validateUrlOrShowError(): String? {
        val result = UrlValidator.validate(currentUrlInput())
        if (!result.isValid) {
            urlErrorText.text = result.errorMessage.ifBlank { getString(R.string.invalid_url) }
            urlErrorText.visibility = View.VISIBLE
            urlEditText.requestFocus()
            return null
        }
        urlErrorText.visibility = View.GONE
        return result.normalizedUrl
    }

    private fun testWebsite() {
        val url = validateUrlOrShowError() ?: return
        btnTestWebsite.isEnabled = false
        btnTestWebsite.text = "Testing…"
        lifecycleScope.launch {
            val probe = withContext(Dispatchers.IO) {
                NetworkUtils.probeUrl(url)
            }
            btnTestWebsite.isEnabled = true
            btnTestWebsite.text = getString(R.string.test_website)
            if (probe.reachable) {
                Toast.makeText(
                    this@SettingsActivity,
                    "${getString(R.string.url_reachable)} (${probe.message})",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(
                    this@SettingsActivity,
                    "${getString(R.string.url_unreachable)}\n${probe.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun saveAndLoad() {
        val url = validateUrlOrShowError() ?: return
        val newSettings = AppSettings(
            websiteUrl = url,
            fullscreenEnabled = fullscreenEnabled,
            landscapeEnabled = landscapeEnabled,
            dpadNavigationEnabled = dpadEnabled,
            allowExternalNavigation = !blockExternal,
            allowPopups = !blockPopups,
            rememberLastPage = rememberLast,
            lastPageUrl = if (rememberLast) settings.lastPageUrl else "",
            lastNavigationError = settings.lastNavigationError
        )
        settingsRepository.saveSettings(newSettings)
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_FORCE_RELOAD, true)
        )
        finish()
    }

    private fun confirmReset() {
        AlertDialog.Builder(this)
            .setTitle(R.string.reset_confirm_title)
            .setMessage(R.string.reset_confirm_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.reset) { _, _ ->
                settingsRepository.resetToDefaults()
                settings = settingsRepository.getSettings()
                loadFromSettings(settings)
                Toast.makeText(this, "Defaults restored", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun confirmClearData() {
        AlertDialog.Builder(this)
            .setTitle(R.string.clear_data_title)
            .setMessage(R.string.clear_data_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.clear) { _, _ ->
                // Clear cookies/cache via a temporary WebView
                val wv = android.webkit.WebView(this)
                android.webkit.CookieManager.getInstance().removeAllCookies {
                    android.webkit.CookieManager.getInstance().flush()
                    wv.clearCache(true)
                    wv.clearHistory()
                    wv.clearFormData()
                    android.webkit.WebStorage.getInstance().deleteAllData()
                    wv.destroy()
                    Toast.makeText(this, R.string.data_cleared, Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }
}
