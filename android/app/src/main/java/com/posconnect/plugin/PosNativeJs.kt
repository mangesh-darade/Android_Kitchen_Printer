package com.posconnect.plugin

object PosNativeJs {
    const val INTERFACE_NAME = "POSNativeBridge"

    val PRINT_HOOK: String = """
        (function() {
          if (window.__POS_PRINT_HOOK) { return; }
          var raw = window.POSNativeBridge;
          if (!raw || typeof raw.showPrintDialog !== 'function') { return; }
          window.__POS_PRINT_HOOK = true;
          var __origPrint = window.print ? window.print.bind(window) : function() {};
          window.print = function() {
            var text = '';
            try {
              text = (document.body && (document.body.innerText || document.body.textContent)) || '';
            } catch (e) {}
            raw.showPrintDialog(JSON.stringify({ text: text }));
          };
        })();
    """.trimIndent()

    val WRAPPER: String = """
        (function() {
          if (window.__POS_NATIVE_READY) { return; }
          var raw = window.POSNativeBridge;
          if (!raw) { return; }
          function parse(value) {
            try { return JSON.parse(value); } catch (e) { return { success: false, errorCode: 'INVALID_RECEIPT', message: String(e) }; }
          }
          function asString(data) {
            if (data == null) return '';
            return typeof data === 'string' ? data : JSON.stringify(data);
          }
          window.POSNative = {
            getDeviceInfo: async function() { return parse(raw.getDeviceInfo()); },
            getConfiguration: async function() { return parse(raw.getConfiguration()); },
            getPrinterStatus: async function() { return parse(raw.getPrinterStatus()); },
            getPrinters: async function() { return parse(raw.getPrinters()); },
            getPrinterCapabilities: async function() { return parse(raw.getPrinterCapabilities()); },
            getPrinterWidth: async function() { return parse(raw.getPrinterWidth()); },
            getPrinterSettings: async function() { return parse(raw.getPrinterSettings()); },
            getConnectionStatus: async function() { return parse(raw.getConnectionStatus()); },
            connectPrinter: async function(config) { return parse(raw.connectPrinter(asString(config))); },
            disconnectPrinter: async function() { return parse(raw.disconnectPrinter()); },
            printReceipt: async function(data) { return parse(raw.printReceipt(asString(data))); },
            printText: async function(data) { return parse(raw.printText(asString(data))); },
            printImage: async function(data) { return parse(raw.printImage(asString(data))); },
            printQRCode: async function(data) { return parse(raw.printQRCode(asString(data))); },
            printBarcode: async function(data) { return parse(raw.printBarcode(asString(data))); },
            testPrinter: async function() { return parse(raw.testPrinter()); },
            openCashDrawer: async function() { return parse(raw.openCashDrawer()); },
            cutPaper: async function() { return parse(raw.cutPaper()); },
            beep: async function() { return parse(raw.beep()); },
            showPrintDialog: async function(data) { return parse(raw.showPrintDialog(asString(data))); }
          };
          window.posNativeBridge = window.posNativeBridge || {};
          if (typeof window.posNativeBridge._printResult !== 'function') {
            window.posNativeBridge._printResult = function() {};
          }
          window.ElintPOSNative = window.ElintPOSNative || {
            printWebContent: function(text, _mode) {
              try {
                var result = parse(raw.printText(JSON.stringify({ text: String(text || '') })));
                return JSON.stringify({ ok: result.success === true });
              } catch (e) {
                return JSON.stringify({ ok: false });
              }
            }
          };
          window.__POS_NATIVE_READY = true;
        })();
    """.trimIndent()
}
