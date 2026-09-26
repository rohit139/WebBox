package com.webbox.tv.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import java.net.HttpURLConnection
import java.net.URL

object NetworkUtils {

    fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
        @Suppress("DEPRECATION")
        return cm.activeNetworkInfo?.isConnected == true
    }

    /**
     * Lightweight reachability probe. Does not treat HTTP 401/403 as failure.
     */
    fun probeUrl(url: String, timeoutMs: Int = 8000): ProbeResult {
        return try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                requestMethod = "GET"
                setRequestProperty("User-Agent", "WebBox/1.0")
            }
            try {
                val code = connection.responseCode
                ProbeResult(
                    reachable = code in 100..599,
                    httpCode = code,
                    message = "HTTP $code"
                )
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            ProbeResult(reachable = false, httpCode = -1, message = e.message ?: "Connection failed")
        }
    }

    data class ProbeResult(
        val reachable: Boolean,
        val httpCode: Int,
        val message: String
    )
}
