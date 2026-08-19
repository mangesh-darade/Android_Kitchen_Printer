package com.posconnectrn

import android.annotation.SuppressLint
import android.graphics.Color
import android.net.http.SslError
import android.os.Build
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactContext
import com.facebook.react.uimanager.events.RCTEventEmitter
import com.posconnect.bridge.POSNativeBridge
import com.posconnect.core.config.ConfigurationRepository
import com.posconnect.core.security.SecurityManager
import com.posconnect.plugin.NetworkHelper
import com.posconnect.plugin.PosNativeJs

@SuppressLint("SetJavaScriptEnabled", "ViewConstructor")
class POSWebView(private val reactContext: ReactContext) : WebView(reactContext) {

    private var bridgeAttached = false
    private var currentUrl = ""

    init {
        setBackgroundColor(Color.parseColor("#FFFFFF"))
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = false
            displayZoomControls = false
            setSupportZoom(false)
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                emitEvent("onLoadProgress", newProgress)
            }
        }

        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                val config = ConfigurationRepository.getInstance(reactContext).configState.value
                if (SecurityManager.isOriginAllowed(url, config)) {
                    return false
                }
                if (!config.security.allowExternalNavigation) {
                    return true
                }
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                attachBridgeIfNeeded()
                view?.evaluateJavascript(PosNativeJs.WRAPPER, null)
                view?.evaluateJavascript(PosNativeJs.PRINT_HOOK, null)
                emitEvent("onLoadEnd", url ?: "")
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request?.isForMainFrame == true) {
                    val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) error?.errorCode ?: -1 else -1
                    val desc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) error?.description?.toString() else null
                    emitEvent("onError", NetworkHelper.friendlyWebViewError(code, desc))
                }
            }

            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                handler?.cancel()
                emitEvent("onError", "SSL certificate error. Contact your POS administrator.")
            }
        }
    }

    fun loadPosUrl(url: String) {
        currentUrl = url.trim()
        if (currentUrl.isBlank()) {
            emitEvent("onError", "No Division URL configured.")
            return
        }
        val blockMessage = NetworkHelper.messageBeforeLoad(reactContext, currentUrl)
        if (blockMessage != null) {
            emitEvent("onError", blockMessage)
            return
        }
        attachBridgeIfNeeded()
        loadUrl(currentUrl)
    }

    private fun attachBridgeIfNeeded() {
        if (bridgeAttached) return
        val bridge = POSNativeBridge(
            context = reactContext,
            webViewProvider = { this@POSWebView },
            activityProvider = { reactContext.currentActivity },
        )
        addJavascriptInterface(bridge, PosNativeJs.INTERFACE_NAME)
        bridgeAttached = true
    }

    private fun emitEvent(eventName: String, value: Any) {
        val event = Arguments.createMap().apply {
            when (value) {
                is String -> putString("message", value)
                is Int -> putInt("progress", value)
                else -> putString("message", value.toString())
            }
        }
        reactContext.getJSModule(RCTEventEmitter::class.java)
            .receiveEvent(id, eventName, event)
    }
}
