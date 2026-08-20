# Vendor printer SDK binaries

| Brand | Status | Files | Source |
|-------|--------|-------|--------|
| **Epson** | ✅ Bundled | `epson/ePOS2.jar`, `epson/ePOSEasySelect.jar`, `jniLibs/*/libepos2.so` | [Official Epson CDN v2.32.0](https://download3.ebz.epson.net/dsc/f/03/00/17/07/34/7de19987ac4424b34b1ee708f254d7b825526beb/ePOS_SDK_Android_v2.32.0.zip) |
| **XPrinter** | ✅ Bundled | `xprinter/printer-lib-3.2.0.aar` | [flutter_xprinter_sdk](https://github.com/Lazizbek97/flutter_xprinter_sdk) (official XPrinter AAR) |
| **GPrinter** | ✅ Bundled | `gprinter/gprintersdk-2.0.aar` | Maven mirror (official `com.gprinter:gprintersdk`) |
| **SUNMI** | ✅ Maven | `com.sunmi:printerlibrary:1.0.24` | Maven Central |
| **Star** | ✅ npm | `react-native-star-io10` | npm |
| **Rongta** | ⚠️ Manual | `rongta/*.jar` or `*.aar` | [rongtatech.com/sdk](https://www.rongtatech.com/sdk/) OneDrive link |

## Re-download all bundled SDKs

```powershell
powershell -File scripts/download-vendor-sdks.ps1
```

## Rongta (manual only)

1. Open https://www.rongtatech.com/sdk/
2. Download **Thermal Printer & Label Printer Android SDK** (OneDrive)
3. Copy `printer_library.jar` into `rongta/`

Settings screen will show **Official SDK integrated** once the JAR is present.
