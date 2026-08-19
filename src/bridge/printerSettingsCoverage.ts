import {emptyConfig, type AppConfig, type PrinterConfig} from '../core/config/models';
import {PRINTER_OPERATOR_SETTING_KEYS, PRINTER_SETTINGS_BRIDGE_KEYS, SETUP_DRAFT_PRINTER_KEYS} from './posNativeContract';

export function printerSettingsForBridge(printer: PrinterConfig) {
  return {
    showPrintDialog: printer.showPrintDialog,
    autoCut: printer.autoCut,
    cutMode: printer.cutMode,
    width: printer.width,
    charactersPerLine: printer.profile.charactersPerLine,
    widthMm: printer.profile.widthMm,
    printableWidthMm: printer.profile.printableWidthMm,
    cutSupported: printer.profile.cutSupported,
    printEngine: printer.printEngine,
    brand: printer.brand,
    connection: printer.connection,
    cashDrawer: printer.cashDrawer,
    starIdentifier: printer.starIdentifier,
    ip: printer.ip,
    port: printer.port,
    cloudPrntUrl: printer.cloudPrntUrl,
    macAddress: printer.macAddress,
    passPrntPort: printer.passPrntPort,
    passPrntSettings: printer.passPrntSettings,
    name: printer.name,
    role: printer.role,
    enabled: printer.enabled,
    deviceName: printer.deviceName,
    usbVendorId: printer.usbVendorId,
    usbProductId: printer.usbProductId,
    autoReconnect: printer.autoReconnect,
    retryCount: printer.retryCount,
  };
}

export function draftCoversOperatorSettings(draft: Record<string, unknown>): boolean {
  return SETUP_DRAFT_PRINTER_KEYS.every(key => key in draft);
}

export function configPrinterHasOperatorSettings(config: AppConfig = emptyConfig()): boolean {
  return PRINTER_OPERATOR_SETTING_KEYS.every(key => key in config.printer);
}

export function bridgeSettingsHasRequiredKeys(settings: Record<string, unknown>): boolean {
  return PRINTER_SETTINGS_BRIDGE_KEYS.every(key => key in settings);
}
