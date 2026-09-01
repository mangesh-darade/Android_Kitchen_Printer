/** Kitchen + POS Native JS bridge contract. Keep in sync with PosNativeJs.kt and iOS inject. */
export const POS_NATIVE_BRIDGE_METHODS = [
  'getDeviceInfo',
  'getConfiguration',
  'getPrinterStatus',
  'getPrinterCapabilities',
  'getPrinters',
  'connectPrinter',
  'disconnectPrinter',
  'printReceipt',
  'printText',
  'printImage',
  'printQRCode',
  'printBarcode',
  'testPrinter',
  'openCashDrawer',
  'cutPaper',
  'beep',
  'showPrintDialog',
  'getPrinterSettings',
  'getPrinterWidth',
  'getConnectionStatus',
] as const;

export const POS_NATIVE_ASYNC_METHODS = [...POS_NATIVE_BRIDGE_METHODS] as const;

/** Settings the kitchen/POS website reads from getPrinterSettings(). */
export const PRINTER_SETTINGS_BRIDGE_KEYS = [
  'showPrintDialog',
  'autoCut',
  'cutMode',
  'width',
  'charactersPerLine',
  'widthMm',
  'printableWidthMm',
  'cutSupported',
  'printEngine',
  'brand',
  'connection',
  'starIdentifier',
  'ip',
  'port',
  'macAddress',
  'name',
  'enabled',
  'cutIncludedInPrint',
  'sdkTechName',
  'sdkOfficialName',
  'sdkVersion',
  'sdkSupply',
  'sdkIntegrated',
  'sdkDownloadUrl',
  'sdkPrintPath',
  'sdkUsesVendorApi',
] as const;

/** Printer fields the operator must be able to set (Setup and/or Settings). */
export const PRINTER_OPERATOR_SETTING_KEYS = [
  'brand',
  'printEngine',
  'connection',
  'width',
  'ip',
  'port',
  'macAddress',
  'starIdentifier',
  'model',
  'showPrintDialog',
  'autoCut',
  'cutMode',
  'name',
  'enabled',
] as const;

/** SetupDraft uses printerIp/printerPort instead of ip/port. */
export const SETUP_DRAFT_PRINTER_KEYS = [
  'brand',
  'printEngine',
  'connection',
  'width',
  'printerIp',
  'printerPort',
  'macAddress',
  'starIdentifier',
  'model',
  'showPrintDialog',
  'autoCut',
  'cutMode',
  'printerName',
  'printerEnabled',
] as const;

export type PrinterOperatorSettingKey = (typeof PRINTER_OPERATOR_SETTING_KEYS)[number];
