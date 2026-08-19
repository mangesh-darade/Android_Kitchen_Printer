package com.posconnect.plugin

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.posconnect.core.logging.DiagnosticLogger
import com.posconnect.core.logging.LogCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetAddress
import java.net.URI
import java.net.UnknownHostException

object NetworkHelper {

    private const val DNS_TIMEOUT_MS = 4_000L

    fun isNetworkAvailable(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = manager.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
    }

    suspend fun canResolveHost(url: String): Boolean = withContext(Dispatchers.IO) {
        val host = extractHost(url) ?: return@withContext false
        try {
            val addresses = withTimeoutOrNull(DNS_TIMEOUT_MS) {
                InetAddress.getAllByName(host)
            } ?: run {
                DiagnosticLogger.w(LogCategory.NETWORK, "NetworkHelper", "DNS timed out for $host")
                return@withContext false
            }
            val ok = addresses.isNotEmpty()
            DiagnosticLogger.i(
                LogCategory.NETWORK,
                "NetworkHelper",
                "DNS resolved $host -> ${addresses.firstOrNull()?.hostAddress ?: "none"}"
            )
            ok
        } catch (e: UnknownHostException) {
            DiagnosticLogger.w(LogCategory.NETWORK, "NetworkHelper", "DNS failed for $host: ${e.message}")
            false
        } catch (e: Exception) {
            DiagnosticLogger.w(LogCategory.NETWORK, "NetworkHelper", "DNS check error for $host: ${e.message}")
            false
        }
    }

    fun extractHost(url: String): String? {
        return try {
            URI(url.trim()).host
        } catch (_: Exception) {
            null
        }
    }

    fun messageBeforeLoad(context: Context, url: String): String? {
        if (!isNetworkAvailable(context)) {
            return "No internet connection. Connect Wi‑Fi or mobile data, then tap Retry."
        }
        if (extractHost(url).isNullOrBlank()) {
            return "Invalid Division URL."
        }
        return null
    }

    fun friendlyWebViewError(errorCode: Int, description: String?): String {
        val desc = description.orEmpty()
        return when {
            desc.contains("ERR_NAME_NOT_RESOLVED", ignoreCase = true) ||
                errorCode == android.webkit.WebViewClient.ERROR_HOST_LOOKUP ||
                errorCode == -2 ->
                "DNS error: hostname could not be resolved.\nCheck internet and DNS on this device."

            desc.contains("ERR_INTERNET_DISCONNECTED", ignoreCase = true) ||
                errorCode == android.webkit.WebViewClient.ERROR_CONNECT ->
                "Device is offline. Connect Wi‑Fi or mobile data."

            desc.contains("ERR_CONNECTION_TIMED_OUT", ignoreCase = true) ||
                errorCode == android.webkit.WebViewClient.ERROR_TIMEOUT ->
                "Connection timed out. Server may be down or network is slow."

            desc.contains("ERR_SSL", ignoreCase = true) ||
                errorCode == android.webkit.WebViewClient.ERROR_FAILED_SSL_HANDSHAKE ->
                "SSL certificate error. Contact your POS administrator."

            desc.isNotBlank() -> desc
            else -> "Network error (code $errorCode)"
        }
    }
}
