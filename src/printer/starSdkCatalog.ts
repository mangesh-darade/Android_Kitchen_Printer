/**
 * Official Star Micronics printer SDKs from
 * https://starmicronics.com/support/developers/printer-sdks/
 *
 * Android SDK + iOS SDK are consumed through the official Cross-Platform
 * React Native package (StarXpand / StarIO10). We do NOT also link native
 * StarIO10 AARs/frameworks beside it — that would duplicate the same SDK.
 */
export const STAR_SDK_CATALOG = [
  {
    id: 'android-sdk',
    title: 'Android SDK (StarIO10 / StarXpand Kotlin)',
    inApp: true,
    how: 'Bundled AAR inside react-native-star-io10 (same StarIO10 as native Android SDK)',
  },
  {
    id: 'ios-sdk',
    title: 'iOS SDK (StarIO10 / StarXpand Swift)',
    inApp: true,
    how: 'Bundled framework inside react-native-star-io10 (same StarIO10 as native iOS SDK)',
  },
  {
    id: 'react-native-sdk',
    title: 'Cross-Platform React Native SDK',
    inApp: true,
    how: 'npm react-native-star-io10 — discovery, print, status, Star Configuration',
  },
  {
    id: 'passprnt',
    title: 'PassPRNT (URL-scheme printing)',
    inApp: true,
    how: 'src/printer/passPrnt.ts → starpassprnt://v1/print/nopreview',
  },
  {
    id: 'cloudprnt',
    title: 'CloudPRNT',
    inApp: true,
    how: 'src/printer/cloudPrnt.ts HTTP POST text/plain or image/png to CloudPRNT URL',
  },
  {
    id: 'web-sdk',
    title: 'Web SDK (StarXpand for browser)',
    inApp: false,
    how: 'Browser-only. POS website can load it independently — see webSdk.ts. Not bundled here to avoid duplicating StarIO10.',
  },
  {
    id: 'device-manager',
    title: 'Device Manager APIs',
    inApp: true,
    how: 'StarPrinter.getStatus / printer.information after open',
  },
  {
    id: 'command-specs',
    title: 'Command Specs (StarXpandCommandBuilder)',
    inApp: true,
    how: 'src/printer/starCommands.ts text, QR, barcode, image, cut, feed, drawer',
  },
  {
    id: 'utility-config',
    title: 'Utility / Star Configuration Format',
    inApp: true,
    how: 'getStarConfiguration / getDefaultStarConfiguration / setStarConfiguration',
  },
  {
    id: 'windows-sdk',
    title: 'Windows SDK',
    inApp: false,
    how: 'Out of scope — this app is Android + iOS only',
  },
] as const;

export type StarSdkCatalogId = (typeof STAR_SDK_CATALOG)[number]['id'];

export function inAppStarSdks() {
  return STAR_SDK_CATALOG.filter(item => item.inApp);
}

export function documentedOutOfAppStarSdks() {
  return STAR_SDK_CATALOG.filter(item => !item.inApp);
}
