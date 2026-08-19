import {cloudPrntHeaders} from './cloudPrnt';
import {documentedOutOfAppStarSdks, inAppStarSdks, STAR_SDK_CATALOG} from './starSdkCatalog';
import {STAR_WEB_SDK, webSdkIsNativeDuplicate} from './webSdk';

describe('Star SDK catalog — nothing skipped', () => {
  it('covers every product on the Star printer-sdks page', () => {
    const ids = STAR_SDK_CATALOG.map(item => item.id);
    expect(ids).toEqual([
      'android-sdk',
      'ios-sdk',
      'react-native-sdk',
      'passprnt',
      'cloudprnt',
      'web-sdk',
      'device-manager',
      'command-specs',
      'utility-config',
      'windows-sdk',
    ]);
  });

  it('ships Android+iOS StarIO10, PassPRNT, CloudPRNT, Device Manager, commands, config', () => {
    const inApp = inAppStarSdks().map(item => item.id);
    expect(inApp).toEqual([
      'android-sdk',
      'ios-sdk',
      'react-native-sdk',
      'passprnt',
      'cloudprnt',
      'device-manager',
      'command-specs',
      'utility-config',
    ]);
  });

  it('documents Web SDK and Windows instead of silently omitting them', () => {
    const documented = documentedOutOfAppStarSdks().map(item => item.id);
    expect(documented).toEqual(['web-sdk', 'windows-sdk']);
    expect(STAR_WEB_SDK.bundledInNativeApp).toBe(false);
    expect(webSdkIsNativeDuplicate()).toBe(true);
  });

  it('CloudPRNT posts text/plain as Star media type', () => {
    expect(cloudPrntHeaders()['Content-Type']).toBe('text/plain');
  });
});
