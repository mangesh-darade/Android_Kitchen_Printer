import type {ConnectionType, PrintEngine, PrinterBrand} from '../core/config/models';

/** How the vendor SDK is supplied in this app build. */
export type VendorSdkSupply = 'bundled' | 'maven' | 'escpos_fallback' | 'manual_required';

export interface VendorSdkEntry {
  brand: PrinterBrand;
  /** Shown in Settings / Setup and exposed via getPrinterSettings(). */
  sdkTechName: string;
  officialSdkName: string;
  version: string;
  supply: VendorSdkSupply;
  downloadUrl: string;
  mavenCoordinate?: string;
  npmPackage?: string;
  notes: string;
}

export const VENDOR_SDK_CATALOG: VendorSdkEntry[] = [
  {
    brand: 'GENERIC_ESC_POS',
    sdkTechName: 'POS Connect ESC/POS Engine',
    officialSdkName: 'Generic ESC/POS (no vendor SDK)',
    version: '3.0',
    supply: 'escpos_fallback',
    downloadUrl: '',
    notes: 'Custom Kotlin ESC/POS over LAN / Bluetooth / BLE / USB.',
  },
  {
    brand: 'EPSON',
    sdkTechName: 'Epson ePOS SDK for Android',
    officialSdkName: 'ePOS2.jar + libepos2.so',
    version: '2.32.0',
    supply: 'bundled',
    downloadUrl: 'https://download3.ebz.epson.net/dsc/f/03/00/17/07/34/7de19987ac4424b34b1ee708f254d7b825526beb/ePOS_SDK_Android_v2.32.0.zip',
    notes: 'Bundled in android/app/libs/epson/ + jniLibs from official Epson CDN.',
  },
  {
    brand: 'STAR',
    sdkTechName: 'StarIO10 / StarXpand SDK',
    officialSdkName: 'Star Micronics StarIO10 (Android + iOS)',
    version: '1.13.0',
    supply: 'bundled',
    downloadUrl: 'https://starmicronics.com/support/developers/printer-sdks/',
    npmPackage: 'react-native-star-io10',
    notes: 'STAR_IO10 engine. ESC/POS mode uses generic engine without Star SDK.',
  },
  {
    brand: 'XPRINTER',
    sdkTechName: 'XPrinter Android SDK',
    officialSdkName: 'printer-lib AAR (net.posprinter)',
    version: '3.2.0',
    supply: 'bundled',
    downloadUrl: 'https://www.xprintertech.com/sdk.html',
    notes: 'Bundled printer-lib-3.2.0.aar in android/app/libs/xprinter/.',
  },
  {
    brand: 'RONGTA',
    sdkTechName: 'Rongta Thermal Printer Android SDK',
    officialSdkName: 'RTPrinterSDK printer_library.jar',
    version: '2025-11',
    supply: 'manual_required',
    downloadUrl: 'https://www.rongtatech.com/sdk/',
    notes: 'Download Android SDK ZIP from Rongta OneDrive link → android/app/libs/rongta/. Until then ESC/POS fallback.',
  },
  {
    brand: 'GPRINTER',
    sdkTechName: 'GPrinter Android SDK',
    officialSdkName: 'com.gprinter:gprintersdk AAR',
    version: '2.0',
    supply: 'bundled',
    downloadUrl: 'http://gprinter.net/kaifa01/',
    notes: 'Bundled gprintersdk-2.0.aar in android/app/libs/gprinter/.',
  },
];

export const STAR_ENGINE_SDK_NAMES: Record<Exclude<PrintEngine, 'ESC_POS'>, string> = {
  STAR_IO10: 'StarXpand SDK',
};

export function catalogEntryForBrand(brand: PrinterBrand): VendorSdkEntry {
  return VENDOR_SDK_CATALOG.find(item => item.brand === brand) ?? VENDOR_SDK_CATALOG[0];
}

export function isSdkBundled(supply: VendorSdkSupply): boolean {
  return supply === 'bundled' || supply === 'maven';
}

export type SdkPrintPath = 'vendor_sdk' | 'escpos_fallback' | 'star_js';

export function resolveSdkPrintPath(
  brand: PrinterBrand,
  printEngine: PrintEngine,
  connection: ConnectionType,
): SdkPrintPath {
  if (printEngine === 'STAR_IO10') return 'star_js';
  if (brand === 'EPSON' || brand === 'XPRINTER' || brand === 'GPRINTER') return 'vendor_sdk';
  if (brand === 'RONGTA') return 'escpos_fallback';
  return 'escpos_fallback';
}

/** Active SDK / engine label shown in printer settings for the current selection. */
export function resolveActiveSdkTechName(
  brand: PrinterBrand,
  printEngine: PrintEngine,
  connection: ConnectionType,
): string {
  if (brand === 'STAR') {
    if (printEngine === 'ESC_POS') {
      return 'Generic ESC/POS (Star LAN/BT fallback — no StarIO10 SDK)';
    }
    return STAR_ENGINE_SDK_NAMES[printEngine];
  }

  const entry = catalogEntryForBrand(brand);



  if (isSdkBundled(entry.supply)) {
    return `${entry.sdkTechName} ${entry.version}`;
  }

  if (entry.supply === 'manual_required') {
    return `${entry.sdkTechName} ${entry.version} (not bundled — ESC/POS fallback)`;
  }

  return `${entry.sdkTechName} ${entry.version}`;
}

export function resolveSdkIntegrationStatus(
  brand: PrinterBrand,
  printEngine: PrintEngine,
  connection: ConnectionType,
): 'integrated' | 'fallback' | 'partial' {
  const path = resolveSdkPrintPath(brand, printEngine, connection);
  if (path === 'vendor_sdk' || path === 'star_js' || path) {
    return 'integrated';
  }
  if (brand === 'RONGTA') {
    return 'fallback';
  }
  const entry = catalogEntryForBrand(brand);
  return entry.supply === 'escpos_fallback' ? 'fallback' : 'partial';
}

export function printerSdkSettingsFields(
  brand: PrinterBrand,
  printEngine: PrintEngine,
  connection: ConnectionType,
) {
  const entry = catalogEntryForBrand(brand);
  const printPath = resolveSdkPrintPath(brand, printEngine, connection);
  return {
    sdkTechName: resolveActiveSdkTechName(brand, printEngine, connection),
    sdkOfficialName: entry.officialSdkName,
    sdkVersion: entry.version,
    sdkSupply: entry.supply,
    sdkIntegrated: resolveSdkIntegrationStatus(brand, printEngine, connection) === 'integrated',
    sdkDownloadUrl: entry.downloadUrl,
    sdkPrintPath: printPath,
    sdkUsesVendorApi: printPath === 'vendor_sdk' || printPath === 'star_js',
  };
}

