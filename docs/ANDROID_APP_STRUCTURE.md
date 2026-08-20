# Android Kitchen Printer — App Structure

**Project:** PosConnectRN (React Native 0.76 + Kotlin)  
**Package:** `com.posconnectrn`  
**Purpose:** ElintOm kitchen display WebView + native thermal printer (Star SDK + ESC/POS)

---

## 1. High-level architecture

```
┌─────────────────────────────────────────────────────────────┐
│  React Native UI (src/)                                      │
│  Welcome → Setup → PosSession → Settings → PrinterTest     │
└──────────────────────────┬──────────────────────────────────┘
                           │ posConnect.ts (NativeModules)
┌──────────────────────────▼──────────────────────────────────┐
│  android/app/.../com/posconnectrn/                           │
│  PosConnectModule · POSWebView · MainActivity                │
└──────────────────────────┬──────────────────────────────────┘
                           │
        ┌──────────────────┼──────────────────┐
        ▼                  ▼                  ▼
  com/posconnect/    ElintOm WebView    Star IO10 (npm)
  printer engine     POSNativeBridge
  ESC/POS LAN/BT/USB window.POSNativeBridge
```

| Layer | Folder | Language | Role |
|-------|--------|----------|------|
| UI | `src/screens/` | TypeScript/React | Setup, settings, WebView shell |
| JS bridge | `src/native/` + `src/bridge/` | TypeScript | RN ↔ native API |
| Print routing | `src/printer/` | TypeScript | StarIO10 / PassPRNT / CloudPRNT / ESC-POS |
| RN native shell | `android/.../posconnectrn/` | Kotlin | Module, WebView, Activity |
| Printer engine | `android/.../posconnect/` | Kotlin | Config, print, transports |

---

## 2. Root project (React Native)

```
Android_Kitchen_Printer/
├── index.js                 # App entry → src/App.tsx
├── app.json                 # App name: PosConnectRN
├── package.json             # npm scripts, dependencies
├── scripts/
│   └── adb-reverse.js       # Metro port 8081 → emulator
├── src/                     # JavaScript/TypeScript (see §3)
├── android/                 # Android native (see §4) ← Android Studio open this
├── ios/                     # iOS stub
└── docs/                    # Documentation
```

---

## 3. `src/` — React Native (screens & logic)

### 3.1 Entry & navigation

| File | काय आहे |
|------|---------|
| `src/App.tsx` | Navigation stack — Welcome, Setup, PosSession, Settings, PrinterTest |
| `src/core/app-identity.ts` | App display name |
| `src/theme/styles.ts` | Colors, padding, radius (shared UI theme) |

### 3.2 Screens (`src/screens/`)

| File | Screen | काय करते |
|------|--------|-----------|
| `WelcomeScreen.tsx` | Welcome | Landing — "Start Setup" |
| `SetupScreen.tsx` | Setup | 4-step wizard: Division URL → Customer → Printer → Review |
| `PosSessionScreen.tsx` | POS Session | Toolbar + ElintOm kitchen WebView |
| `SettingsScreen.tsx` | Settings | Printer/engine edit, test print, reset |
| `PrinterTestScreen.tsx` | Star SDK | Discover, status, cut, drawer, config XML |

### 3.3 Components (`src/components/`)

| File | काय आहे |
|------|---------|
| `FormControls.tsx` | Reusable `Field`, `RowChoice`, `ToggleRow` |
| `POSWebViewNative.tsx` | RN wrapper for native `POSWebView` component |

### 3.4 Config (`src/core/config/`)

| File | काय आहे |
|------|---------|
| `models.ts` | `AppConfig`, printer types, URL validation |
| `migration.ts` | Old config JSON → new format |

### 3.5 Native wrapper (`src/native/`)

| File | काय आहे |
|------|---------|
| `posConnect.ts` | `NativeModules.PosConnect` — save/load config, discover, test print, Star events |

### 3.6 ElintOm bridge (`src/bridge/`)

| File | काय आहे |
|------|---------|
| `posNativeContract.ts` | `window.POSNativeBridge` API shape |
| `posNativeInject.ts` | JS injected into WebView |
| `kitchenPrint.ts` | Parse kitchen print payload from ElintOm |
| `printerSettingsCoverage.ts` | Which settings map to native vs JS |

### 3.7 Printer engine (`src/printer/`)

| File | काय आहे |
|------|---------|
| `enginePolicy.ts` | Brand/engine rules (Star vs ESC/POS) |
| `printRouter.ts` | Route print to correct engine |
| `starSdk.ts` | react-native-star-io10 wrapper |
| `starPrintQueue.ts` | Star print job queue |
| `passPrnt.ts` | PassPRNT intent handling |
| `cloudPrnt.ts` | CloudPRNT HTTP |
| `starCommands.ts` | Star command helpers |
| `imagePayload.ts` | Base64 receipt image handling |
| `starSdkCatalog.ts` | Star SDK feature list |
| `webSdk.ts` | Web SDK notes (documented only) |
| `*.test.ts` | Unit tests |

---

## 4. `android/` — Android Studio project

> **Android Studio:** `File → Open → android/` folder (root नाही)

```
android/
├── settings.gradle          # Project name: PosConnectRN, include :app
├── build.gradle             # SDK versions, StarIO10 flatDir repo
├── gradle.properties        # Hermes, architectures, newArchEnabled=false
├── gradlew / gradlew.bat    # Gradle wrapper scripts
├── gradle/wrapper/          # Gradle version jar + properties
├── local.properties         # sdk.dir (machine-specific, gitignored)
└── app/
    ├── build.gradle         # App module, React Native plugin, dependencies
    ├── proguard-rules.pro   # Release minify rules
    ├── debug.keystore       # Debug signing
    └── src/
        ├── debug/
        │   └── AndroidManifest.xml   # Debug-only manifest overrides
        └── main/
            ├── AndroidManifest.xml   # Permissions, MainActivity, app config
            ├── java/com/posconnectrn/    # RN shell (§4.1)
            ├── java/com/posconnect/      # Business + printer (§4.2–§4.6)
            └── res/                      # Icons, strings, styles (§4.7)
```

---

## 4.1 `com.posconnectrn` — React Native shell

| File | काय आहे |
|------|---------|
| `MainApplication.kt` | RN app init, packages, Hermes, `PosConnectPackage` |
| `MainActivity.kt` | Launcher activity, loads JS component `"PosConnectRN"` |
| `PosConnectModule.kt` | Native module: config, discover, connect, test print, URL check |
| `PosConnectPackage.kt` | Registers `PosConnectModule` + `POSWebViewManager` |
| `POSWebView.kt` | Custom WebView — loads ElintOm URL, injects bridge, progress events |
| `POSWebViewManager.kt` | RN view manager for `<POSWebViewNative />` |

**Flow:** `MainActivity` → RN `App.tsx` → `PosSessionScreen` → `POSWebView` → ElintOm kitchen page

---

## 4.2 `com.posconnect.core` — Config & security

### `core/config/`

| File | काय आहे |
|------|---------|
| `AppConfiguration.kt` | `AppConfig`, `PrinterConfig`, division/customer data classes |
| `ConfigurationRepository.kt` | SharedPreferences save/load, config migration |

### `core/logging/`

| File | काय आहे |
|------|---------|
| `DiagnosticLogger.kt` | Tagged logs (CONFIG, PRINTER, SECURITY, etc.) |

### `core/security/`

| File | काय आहे |
|------|---------|
| `SecurityManager.kt` | Allowed domain check for WebView native calls |

---

## 4.3 `com.posconnect.bridge` — WebView ↔ printer

| File | काय आहे |
|------|---------|
| `POSNativeBridge.kt` | `@JavascriptInterface` — `printText`, `cutPaper`, `openDrawer`, etc. ElintOm `pos_native_bridge.js` हेच call करते |
| `StarPrintBridge.kt` | Star print result events → RN JS (`StarPrintRequest`) |

---

## 4.4 `com.posconnect.plugin` — Helpers

| File | काय आहे |
|------|---------|
| `PosNativeJs.kt` | JavaScript strings injected into WebView |
| `NetworkHelper.kt` | Network / reachability helpers |

---

## 4.5 `com.posconnect.printer` — Thermal printer engine

### `printer/manager/`

| File | काय आहे |
|------|---------|
| `PrinterManager.kt` | Main API — connect, print, discover, status, test |

### `printer/queue/`

| File | काय आहे |
|------|---------|
| `PrintQueueManager.kt` | Print job queue (serial print) |

### `printer/registry/`

| File | काय आहे |
|------|---------|
| `PrinterRegistry.kt` | Factory — brand → adapter + transport |

### `printer/adapters/`

| File | काय आहे |
|------|---------|
| `PrinterAdapter.kt` | Adapter interface |
| `GenericEscPosAdapter.kt` | Generic ESC/POS commands |
| `VendorAdapters.kt` | Star, Epson, etc. vendor-specific |

### `printer/escpos/`

| File | काय आहे |
|------|---------|
| `EscPosCommandBuilder.kt` | ESC/POS byte commands |
| `ReceiptLayoutEngine.kt` | Receipt text layout (columns, wrap) |
| `TextRasterizer.kt` | Text → bitmap for image print |

### `printer/transports/`

| File | Connection | काय आहे |
|------|------------|---------|
| `PrinterTransport.kt` | — | Transport interface |
| `TcpTransport.kt` | LAN Wi‑Fi | IP:9100 socket |
| `BluetoothTransport.kt` | Bluetooth classic | BT pairing |
| `BleTransport.kt` | BLE | Low-energy |
| `UsbTransport.kt` | USB OTG | Vendor/product ID |
| `BuiltInPrinterTransport.kt` | Built-in | Device built-in printer |
| `VirtualPrinterTransport.kt` | Debug | Virtual / test printer |

### `printer/model/`

| File | काय आहे |
|------|---------|
| `DiscoveredPrinter.kt` | Scan result model |
| `PrinterProfile.kt` | Paper width, cols, dots |
| `PrinterResult.kt` | Success/error wrapper |
| `PrinterStatus.kt` | Online, paper out, etc. |
| `ReceiptData.kt` | Text/image receipt payload |

---

## 4.6 `res/` — Android resources

| Path | काय आहे |
|------|---------|
| `res/values/strings.xml` | App name string |
| `res/values/styles.xml` | App theme |
| `res/drawable/rn_edit_text_material.xml` | RN text input drawable |
| `res/mipmap-*/ic_launcher*.png` | App icons |

---

## 4.7 `AndroidManifest.xml` — Permissions

| Permission | Reason |
|------------|--------|
| `INTERNET` | ElintOm URL + Metro (debug) |
| `ACCESS_NETWORK_STATE` / `ACCESS_WIFI_STATE` | Network checks |
| `BLUETOOTH*` / `BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT` | BT/BLE printers |
| `ACCESS_FINE_LOCATION` (≤ API 30) | Legacy BT scan |
| `CHANGE_WIFI_MULTICAST_STATE` | LAN printer discovery |

**App flags:** `usesCleartextTraffic=true` (HTTP kitchen URLs), `MainActivity` exported launcher.

---

## 5. Data flow — Kitchen print

```
ElintOm kitchen page (WebView)
    │
    ▼ window.POSNativeBridge.printText(...)
POSNativeBridge.kt (Kotlin)
    │
    ├── ESC/POS engine → PrinterManager → TcpTransport / BT / USB
    │
    └── Star engine → StarPrintBridge event → RN starSdk.ts → StarIO10
```

---

## 6. Build outputs (gitignored)

| Path | काय आहे |
|------|---------|
| `android/app/build/` | APK, dex, intermediates |
| `android/.gradle/` | Gradle cache |
| `android/.idea/` | Android Studio IDE settings |
| `node_modules/` | npm packages |

---

## 7. Run checklist (Android Studio)

1. Open **`android/`** folder (not project root)
2. Terminal: `npm run android:studio` (Metro + adb reverse)
3. Emulator/device select → ▶ Run **app**
4. Setup wizard → Division URL (`.../screens/display/1`) → Printer config

---

## 8. Related docs

| File | Topic |
|------|-------|
| `README.md` | Install, run, vs Capacitor |
| `docs/KOT_KITCHEN_PRINT_ARCHITECTURE.md` | POS → DB → Kitchen → Android bridge → Print (full flow) |
| `docs/STAR_SDK_PLAN.md` | Star SDK roadmap |
| `docs/IOS_TODO.md` | iOS port on Mac |
