# POS Connect RN

React Native port of **POS Connect** (same business logic as `pos-connect` Capacitor app).

- **Stack:** React Native 0.76 + TypeScript + Kotlin (Android) + Swift (iOS stub)
- **Printer engine:** ESC/POS LAN/BT/USB (existing) **or** StarIO10 / PassPRNT / CloudPRNT (official Star SDKs, Android + iOS)
- **ElintOm integration:** Custom `POSWebView` injects `window.POSNativeBridge` — same API as Capacitor build

## vs Capacitor (`pos-connect`)

| Layer | Capacitor | React Native (this repo) |
|---|---|---|
| Setup UI | Vite + HTML | React Native screens |
| POS session | Native Activity WebView | Custom native `POSWebView` component |
| Config | Native SharedPreferences | Same Kotlin `ConfigurationRepository` |
| Printer | Kotlin plugin | Same Kotlin engine (copied once under `com/posconnect`) |
| iOS | Swift plugin | Stub — full port on Mac |

**No duplication of `app/` reference folder** — single Android printer source under `android/app/.../com/posconnect/`.

## Project layout

```
pos-connect-rn/
  src/                    React Native UI (setup, settings, session)
  android/                RN Android + com.posconnect printer engine
  ios/                    Xcode project (iOS native module TODO on Mac)
```

## Prerequisites

- Node.js 20+
- Android Studio, SDK 36, JDK 17+
- For iOS: Mac + Xcode 16+

## Install & run (Android)

```bash
cd pos-connect-rn
npm install
npm start
# new terminal
npm run android
```

## Setup flow

1. Welcome → Setup wizard (Division URL, Customer, Printer)
2. Save config natively → open Division URL in `POSWebView`
3. ElintOm kitchen (`screens/display/1`) uses `pos_native_bridge.js` → `POSNativeBridge.printText/cutPaper`

## Kitchen URL example

```
https://your-server/ElintOm/screens/display/1
```

## Printer settings (same as Capacitor)

- **Show print dialog** — off for kitchen auto-print
- **Auto cut** — on
- **Cut mode** — partial / full
- **Paper** — 3" (48 CPL) / 4" (64 CPL)

## Build release APK

```bash
cd android
./gradlew assembleRelease
```

## iOS status

Android is fully wired. iOS needs `PosConnectModule` + `POSWebView` Swift port (mirror Capacitor iOS plugin in `pos-connect/plugins/pos-connect-native/ios`).

## Related

- Capacitor version: `../pos-connect`
- ElintOm kitchen bridge: `../ElintOm/themes/default/assets/pos/js/pos_native_bridge.js`
