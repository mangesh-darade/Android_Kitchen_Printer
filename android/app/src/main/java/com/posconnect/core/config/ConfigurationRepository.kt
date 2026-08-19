package com.posconnect.core.config

import android.content.Context
import android.content.SharedPreferences
import com.posconnect.core.logging.DiagnosticLogger
import com.posconnect.core.logging.LogCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.net.URI

object ConfigurationMigrationManager {
    const val CURRENT_CONFIG_VERSION = 2

    fun migrate(json: JSONObject, fromVersion: Int): JSONObject {
        DiagnosticLogger.i(LogCategory.CONFIGURATION, "Migration", "Migrating config from version $fromVersion to $CURRENT_CONFIG_VERSION")
        // Future migrations can transform json structure here
        json.put("configVersion", CURRENT_CONFIG_VERSION)
        return json
    }
}

class ConfigurationRepository(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("pos_connect_config_prefs", Context.MODE_PRIVATE)

    private val _configState = MutableStateFlow(loadConfig())
    val configState: StateFlow<AppConfig> = _configState.asStateFlow()

    companion object {
        private const val KEY_CONFIG_JSON = "key_app_config_json"

        @Volatile
        private var instance: ConfigurationRepository? = null

        fun getInstance(context: Context): ConfigurationRepository {
            return instance ?: synchronized(this) {
                instance ?: ConfigurationRepository(context.applicationContext).also { instance = it }
            }
        }
    }

    private fun loadConfig(): AppConfig {
        val rawJson = prefs.getString(KEY_CONFIG_JSON, null)
        if (rawJson.isNullOrBlank()) {
            DiagnosticLogger.i(LogCategory.CONFIGURATION, "ConfigRepo", "No saved config found, using initial default")
            return AppConfig()
        }

        return try {
            var json = JSONObject(rawJson)
            val version = json.optInt("configVersion", 1)
            if (version < ConfigurationMigrationManager.CURRENT_CONFIG_VERSION) {
                json = ConfigurationMigrationManager.migrate(json, version)
            }
            val config = AppConfig.fromJson(json)
            DiagnosticLogger.i(LogCategory.CONFIGURATION, "ConfigRepo", "Loaded configuration successfully (setupCompleted=${config.setupCompleted})")
            config
        } catch (e: Exception) {
            DiagnosticLogger.e(LogCategory.CONFIGURATION, "ConfigRepo", "Failed to parse saved config: ${e.message}")
            AppConfig()
        }
    }

    fun saveConfiguration(config: AppConfig) {
        val jsonStr = config.toJson().toString()
        prefs.edit().putString(KEY_CONFIG_JSON, jsonStr).apply()
        _configState.value = config
        DiagnosticLogger.i(LogCategory.CONFIGURATION, "ConfigRepo", "Saved app configuration (setupCompleted=${config.setupCompleted})")
    }

    fun updateDivision(division: DivisionConfig) {
        val current = _configState.value
        val updatedSecurity = current.security.copy(
            allowedDomains = extractAllowedDomains(division.url, current.security.allowedDomains)
        )
        val updated = current.copy(division = division, security = updatedSecurity)
        saveConfiguration(updated)
    }

    fun updateCustomer(customer: CustomerConfig) {
        val current = _configState.value
        val updated = current.copy(customer = customer)
        saveConfiguration(updated)
    }

    fun updatePrinter(printer: PrinterConfig) {
        val current = _configState.value
        val updated = current.copy(printer = printer)
        saveConfiguration(updated)
    }

    fun completeSetup(
        division: DivisionConfig,
        customer: CustomerConfig,
        printer: PrinterConfig
    ) {
        val allowed = extractAllowedDomains(division.url, emptyList())
        val security = SecurityConfig(
            allowedDomains = allowed,
            allowExternalNavigation = false,
            requireHttps = division.url.startsWith("https://", ignoreCase = true)
        )
        val newConfig = AppConfig(
            configVersion = ConfigurationMigrationManager.CURRENT_CONFIG_VERSION,
            setupCompleted = true,
            division = division,
            customer = customer,
            printer = printer,
            security = security
        )
        saveConfiguration(newConfig)
        DiagnosticLogger.i(LogCategory.CONFIGURATION, "ConfigRepo", "First-launch setup wizard completed successfully")
    }

    fun resetPrinterOnly() {
        val current = _configState.value
        val updated = current.copy(
            printer = PrinterConfig(enabled = false)
        )
        saveConfiguration(updated)
        DiagnosticLogger.w(LogCategory.CONFIGURATION, "ConfigRepo", "Printer configuration reset to default")
    }

    fun resetPrinter() = resetPrinterOnly()

    fun resetApplication() {
        prefs.edit().remove(KEY_CONFIG_JSON).apply()
        val emptyConfig = AppConfig(setupCompleted = false)
        _configState.value = emptyConfig
        DiagnosticLogger.w(LogCategory.CONFIGURATION, "ConfigRepo", "Full application configuration reset. Restarting setup flow.")
    }

    fun resetAll() = resetApplication()

    fun validateUrl(inputUrl: String): Pair<Boolean, String> {
        val trimmed = inputUrl.trim()
        if (trimmed.isEmpty()) {
            return false to "Please enter a valid POS URL."
        }
        if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
            return false to "URL must start with https:// or http://"
        }
        return try {
            val uri = URI(trimmed)
            if (uri.host.isNullOrBlank()) {
                false to "Invalid domain in POS URL."
            } else {
                true to ""
            }
        } catch (e: Exception) {
            false to "Please enter a valid POS URL: ${e.localizedMessage}"
        }
    }

    private fun extractAllowedDomains(url: String, existing: List<String>): List<String> {
        val list = existing.toMutableList()
        try {
            val uri = URI(url.trim())
            uri.host?.let { host ->
                if (!list.contains(host)) list.add(host)
                // Add apex domain if subdomain exists
                val parts = host.split(".")
                if (parts.size >= 2) {
                    val root = parts.takeLast(2).joinToString(".")
                    if (!list.contains(root)) list.add(root)
                }
            }
        } catch (_: Exception) {}
        return list.distinct()
    }
}
