# Star Micronics SDK — Full Integration Plan

**App:** `pos-connect-rn` (React Native)  
**Official SDKs:** [Star Printer SDKs](https://starmicronics.com/support/developers/printer-sdks/)  
**StarXpand RN manual:** https://www.star-m.jp/products/s_print/sdk/react-native-star-io10/manual/en/about.html  
**npm:** `react-native-star-io10` → GitHub [star-micronics/react-native-star-io10](https://github.com/star-micronics/react-native-star-io10)

---

## 1. What Star offers (nothing skipped)

| SDK from Star page | How we use it |
|---|---|
| **Android SDK** (StarIO10 / StarXpand Kotlin) | Bundled inside `react-native-star-io10` Android AAR |
| **iOS SDK** (StarIO10 / StarXpand Swift) | Bundled inside `react-native-star-io10` iOS framework |
| **Cross-Platform React Native SDK** | Direct npm: `react-native-star-io10` (StarXpand) |
| **PassPRNT** | URL-scheme fallback (`starpassprnt://v1/print/nopreview?...`) |
| **CloudPRNT** | HTTP POST of print job to CloudPRNT server URL |
| **Web SDK** | Not in native app (browser-only). Documented; POS website can use it independently |
| **Device Manager APIs** | Printer status / detail via `StarPrinter.getStatus` / `printer.getInformation` |
| **Command Specs** | StarXpandCommandBuilder (text, QR, barcode, cut, drawer) |
| **Utility / Config format** | Settings UI stores identifier, interface, paper, cut |
| **Windows SDK** | Out of scope (Android + iOS only, as requested) |

StarIO10 is **not** compatible with old StarIO / StarIOExtension. We use **StarIO10 only**.

---

## 2. Print engines (router)

```
KOT / receipt text
    │
    ├─ printEngine = STAR_IO10   → react-native-star-io10 (Android + iOS native)
    ├─ printEngine = PASSPRNT    → open PassPRNT app URL scheme
    ├─ printEngine = CLOUDPRNT   → HTTP POST to CloudPRNT URL
    └─ printEngine = ESC_POS     → existing Kotlin Generic ESC/POS (non-Star brands)
```

When **brand = STAR**, default engine is **STAR_IO10**.

---

## 3. StarIO10 APIs we must wire

- Discovery: `StarDeviceDiscoveryManagerFactory.create([Lan, Bluetooth, BluetoothLE, Usb, UsbVendor])`
- Connect: `StarConnectionSettings` + `StarPrinter.open()`
- Print: `StarXpandCommand.StarXpandCommandBuilder` → `printer.print(commands)`
- Text: `PrinterBuilder.actionPrintText`
- Cut: `actionCut(CutType.Partial | Full)`
- Feed: `actionFeedLine`
- QR / barcode: `actionPrintQRCode` / `actionPrintBarcode`
- Cash drawer: `DrawerBuilder.actionOpenDrawer`
- Status: `printer.getStatus()` (paper empty, cover, offline)
- Close/dispose: `printer.close()` / `dispose()`
- Logging: StarIO10 log (surface in diagnostics)

---

## 4. UI (setup + settings) — all fields

- Brand: Generic ESC/POS | **Star Micronics** | Epson | others
- Print engine: StarIO10 | PassPRNT | CloudPRNT | ESC/POS
- Interface: LAN | Bluetooth | BLE | USB
- Star identifier (IP or MAC)
- Paper 3" / 4"
- Print dialog on/off
- Auto cut on/off
- Cut: Partial / Full
- Cash drawer on/off
- CloudPRNT URL (when engine = CloudPRNT)
- Discover Star printers (all interfaces)
- Test print / open drawer / get status

---

## 5. WebView KOT path

ElintOm calls `POSNativeBridge.printText`. For STAR_IO10:

```
Kotlin POSNativeBridge.printText
  → emit RN event StarPrintRequest { text, jobId, settings }
  → RN starSdk.printText()
  → NativeModules.PosConnect.notifyPrintResult(jobId, ok)
  → Kotlin calls window.posNativeBridge._printResult(ok)
```

---

## 6. Native project wiring

**Android**
- `npm install react-native-star-io10`
- Gradle `flatDir` → `node_modules/react-native-star-io10/android/src/lib`
- Permissions: BT scan/connect, BLE (neverForLocation), USB host, INTERNET
- BLE only Android 12+

**iOS**
- CocoaPods after npm install
- Info.plist: `NSBluetoothAlwaysUsageDescription`, `NSBluetoothPeripheralUsageDescription`
- MFi printers: External Accessory protocol if required by Star docs
- PassPRNT: `LSApplicationQueriesSchemes` = `starpassprnt`

---

## 7. Tests (own)

- starSdk command builder: text + partial/full cut + drawer
- print router: STAR vs ESC_POS vs PASSPRNT vs CLOUDPRNT
- models migration: new Star fields default safely
- UI draft maps brand STAR → engine STAR_IO10

---

## 8. Files (implemented)

```
src/printer/enginePolicy.ts      brand → engine, no duplicate LAN
src/printer/starSdk.ts           StarIO10 discovery/print/status/config/logger
src/printer/starCommands.ts      StarXpand text/QR/barcode/cut/drawer
src/printer/passPrnt.ts          PassPRNT URL scheme
src/printer/cloudPrnt.ts         CloudPRNT HTTP POST
src/printer/webSdk.ts            Web SDK documented, not bundled
src/printer/printRouter.ts       one engine per job
src/printer/*.test.ts            own tests
src/screens/PrinterTestScreen.tsx
android/.../StarPrintBridge.kt   WebView KOT → RN
ios/PosConnectRN/PosConnectModule.m
ios/PosConnectRN/Info.plist      BT + LAN + MFi + PassPRNT
```

LAN ESC/POS (`TcpTransport` port 9100) is unchanged and used **only** when `printEngine = ESC_POS`.

---

## 9. Honest limits

- Physical Star printer not in this environment → unit tests + compile, not live print
- iOS full Xcode build needs a Mac
- CloudPRNT needs a real CloudPRNT server URL
- PassPRNT needs Star PassPRNT app installed on device
- TSP100III graphics-only: `actionPrintText` unsupported — raster fallback documented
