# iOS port

React Native iOS build requires Xcode on macOS. Native stubs are in-repo:

| File | Role |
|---|---|
| `ios/PosConnectRN/PosConnectModule.m` | Config + `StarPrintRequest` events + `notifyPrintResult` |
| `ios/PosConnectRN/POSWebViewManager.m` | WKWebView + `window.POSNativeBridge` |
| `ios/PosConnectRN/Info.plist` | Bluetooth, local network, MFi `jp.star-m.starpro`, PassPRNT scheme |

Printing on iOS uses **StarIO10** via `react-native-star-io10` (same JS router as Android). ESC/POS TCP:9100 stays Android-only.

```bash
cd pos-connect-rn
npm install
cd ios && pod install && cd ..
npm run ios
```
