package com.posconnect.core.security

import com.posconnect.core.config.AppConfig
import com.posconnect.core.logging.DiagnosticLogger
import com.posconnect.core.logging.LogCategory
import java.net.URI

object SecurityManager {

    fun isOriginAllowed(currentUrl: String?, config: AppConfig): Boolean {
        if (currentUrl.isNullOrBlank()) {
            DiagnosticLogger.w(LogCategory.SECURITY, "SecurityManager", "Origin validation failed: Empty currentUrl")
            return false
        }

        if (currentUrl.startsWith("about:blank")) {
            return false
        }

        return try {
            val uri = URI(currentUrl)
            val currentHost = uri.host ?: return false

            // Check against primary division URL
            val divisionUri = try { URI(config.division.url) } catch (_: Exception) { null }
            if (divisionUri?.host != null && currentHost.equals(divisionUri.host, ignoreCase = true)) {
                return true
            }

            // Check against allowed domains list
            for (allowed in config.security.allowedDomains) {
                if (currentHost.equals(allowed, ignoreCase = true) || currentHost.endsWith(".$allowed", ignoreCase = true)) {
                    return true
                }
            }

            DiagnosticLogger.w(
                LogCategory.SECURITY,
                "SecurityManager",
                "Blocked unauthorized origin '$currentHost'. Allowed: ${config.security.allowedDomains + listOfNotNull(divisionUri?.host)}"
            )
            false
        } catch (e: Exception) {
            DiagnosticLogger.e(LogCategory.SECURITY, "SecurityManager", "Error parsing URI '$currentUrl': ${e.message}")
            false
        }
    }
}
