package com.posconnectrn

import com.facebook.react.common.MapBuilder
import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.annotations.ReactProp

class POSWebViewManager : SimpleViewManager<POSWebView>() {

    override fun getName(): String = "POSWebView"

    override fun createViewInstance(reactContext: ThemedReactContext): POSWebView =
        POSWebView(reactContext)

    @ReactProp(name = "url")
    fun setUrl(view: POSWebView, url: String?) {
        if (!url.isNullOrBlank()) {
            view.loadPosUrl(url)
        }
    }

    override fun getExportedCustomDirectEventTypeConstants(): Map<String, Any> =
        MapBuilder.builder<String, Any>()
            .put("onLoadProgress", MapBuilder.of("registrationName", "onLoadProgress"))
            .put("onLoadEnd", MapBuilder.of("registrationName", "onLoadEnd"))
            .put("onError", MapBuilder.of("registrationName", "onError"))
            .put("onTitleReceived", MapBuilder.of("registrationName", "onTitleReceived"))
            .build()

    companion object {
        const val COMMAND_RELOAD = 1
        const val COMMAND_GO_BACK = 2
    }

    override fun getCommandsMap(): Map<String, Int> = mapOf(
        "reload" to COMMAND_RELOAD,
        "goBack" to COMMAND_GO_BACK
    )

    override fun receiveCommand(root: POSWebView, commandId: String, args: com.facebook.react.bridge.ReadableArray?) {
        when (commandId) {
            "reload", COMMAND_RELOAD.toString() -> root.reload()
            "goBack", COMMAND_GO_BACK.toString() -> root.goBackIfPossible()
        }
    }
}
