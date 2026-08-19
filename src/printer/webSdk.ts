/**
 * StarXpand SDK for Web — browser only.
 * Native POS Connect uses react-native-star-io10 instead (same StarIO10).
 * ElintOm / kitchen website may load the Web SDK independently if printing
 * from Chrome, not from this app WebView.
 *
 * Docs: https://starmicronics.com/support/developers/printer-sdks/
 */
export const STAR_WEB_SDK = {
  name: 'StarXpand SDK for Web',
  platform: 'browser',
  docs: 'https://starmicronics.com/support/developers/printer-sdks/',
  bundledInNativeApp: false,
  reason:
    'Web SDK targets Chrome/Edge. This app already ships StarIO10 via react-native-star-io10 for Android and iOS.',
} as const;

export function webSdkIsNativeDuplicate(): boolean {
  return true;
}
