import {
  POS_NATIVE_ASYNC_METHODS,
  POS_NATIVE_BRIDGE_METHODS,
  PRINTER_OPERATOR_SETTING_KEYS,
  PRINTER_SETTINGS_BRIDGE_KEYS,
  SETUP_DRAFT_PRINTER_KEYS,
} from './posNativeContract';
import {POS_NATIVE_PRINT_HOOK_JS, POS_NATIVE_WRAPPER_JS} from './posNativeInject';
import {kitchenPrintViaAndroidBridge, parsePrintPayload} from './kitchenPrint';
import {
  bridgeSettingsHasRequiredKeys,
  configPrinterHasOperatorSettings,
  draftCoversOperatorSettings,
  printerSettingsForBridge,
} from './printerSettingsCoverage';
import {emptyConfig} from '../core/config/models';
import {emptyDraft} from '../native/posConnect';

jest.mock('../printer/starSdk', () => ({
  discoverStarPrinters: async () => [],
  starGetInformation: async () => ({success: true, data: {}}),
  starGetStatus: async () => ({
    connected: false,
    ready: false,
    paperOut: false,
    coverOpen: false,
    offline: true,
    error: null,
  }),
}));

describe('POS native JS bridge contract', () => {
  it('exposes every kitchen/POS method on POSNativeBridge', () => {
    expect(POS_NATIVE_BRIDGE_METHODS).toEqual([
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
      'showPrintDialog',
      'getPrinterSettings',
      'getPrinterWidth',
      'getConnectionStatus',
    ]);
  });

  it('inject wrapper lists the same methods on window.POSNative', () => {
    for (const method of POS_NATIVE_ASYNC_METHODS) {
      expect(POS_NATIVE_WRAPPER_JS).toContain(`${method}:`);
    }
    expect(POS_NATIVE_WRAPPER_JS).toContain('window.POSNative');
    expect(POS_NATIVE_WRAPPER_JS).toContain('window.posNativeBridge');
    expect(POS_NATIVE_WRAPPER_JS).toContain('_printResult');
    expect(POS_NATIVE_WRAPPER_JS).toContain('ElintPOSNative');
    expect(POS_NATIVE_WRAPPER_JS).toContain('printWebContent');
  });

  it('hooks window.print to showPrintDialog', () => {
    expect(POS_NATIVE_PRINT_HOOK_JS).toContain('showPrintDialog');
    expect(POS_NATIVE_PRINT_HOOK_JS).toContain('window.print');
  });
});

describe('ElintOm kitchen printText + cutPaper', () => {
  it('prints JSON { text } and cuts on success (same as pos_native_bridge.js)', () => {
    const cutPaper = jest.fn();
    const printText = jest.fn(() => JSON.stringify({success: true}));
    expect(kitchenPrintViaAndroidBridge({printText, cutPaper}, 'KOT #12')).toBe(true);
    expect(printText).toHaveBeenCalledWith(JSON.stringify({text: 'KOT #12'}));
    expect(cutPaper).toHaveBeenCalled();
  });

  it('does not cut when printer returns failure', () => {
    const cutPaper = jest.fn();
    const printText = jest.fn(() =>
      JSON.stringify({success: false, errorCode: 'PRINTER_OFFLINE'}),
    );
    expect(kitchenPrintViaAndroidBridge({printText, cutPaper}, 'KOT')).toBe(false);
    expect(cutPaper).not.toHaveBeenCalled();
  });

  it('rejects empty KOT text', () => {
    const printText = jest.fn();
    expect(kitchenPrintViaAndroidBridge({printText}, '  ')).toBe(false);
    expect(printText).not.toHaveBeenCalled();
  });

  it('treats completed Star print as success (sync await, not QUEUED)', () => {
    const cutPaper = jest.fn();
    const printText = jest.fn(() =>
      JSON.stringify({success: true, message: 'Printed via StarIO10'}),
    );
    expect(kitchenPrintViaAndroidBridge({printText, cutPaper}, 'KOT')).toBe(true);
    expect(cutPaper).toHaveBeenCalled();
  });

  it('skips separate cutPaper when cutIncludedInPrint is true (Star/PassPRNT)', () => {
    const cutPaper = jest.fn();
    const printText = jest.fn(() => JSON.stringify({success: true}));
    expect(
      kitchenPrintViaAndroidBridge({printText, cutPaper}, 'KOT', {cutIncludedInPrint: true}),
    ).toBe(true);
    expect(cutPaper).not.toHaveBeenCalled();
  });

  it('does not cut when printer returns disabled', () => {
    const cutPaper = jest.fn();
    const printText = jest.fn(() =>
      JSON.stringify({
        success: false,
        errorCode: 'PRINTER_OFFLINE',
        message: 'Printer is disabled',
      }),
    );
    expect(kitchenPrintViaAndroidBridge({printText, cutPaper}, 'KOT')).toBe(false);
    expect(cutPaper).not.toHaveBeenCalled();
  });

  it('parses kitchen JSON and raw text the same way native does', () => {
    expect(parsePrintPayload(JSON.stringify({text: 'KOT A'}))).toBe('KOT A');
    expect(parsePrintPayload('plain kot')).toBe('plain kot');
    expect(parsePrintPayload('')).toBe('');
  });
});

describe('printer settings — nothing missing', () => {
  it('operator can set every printer-related field in Setup draft', () => {
    expect(draftCoversOperatorSettings(emptyDraft() as unknown as Record<string, unknown>)).toBe(
      true,
    );
    expect(configPrinterHasOperatorSettings()).toBe(true);
    expect(PRINTER_OPERATOR_SETTING_KEYS.length).toBe(SETUP_DRAFT_PRINTER_KEYS.length);
  });

  it('getPrinterSettings payload includes Star + kitchen keys', () => {
    const printer = {
      ...emptyConfig().printer,
      brand: 'STAR' as const,
      printEngine: 'STAR_IO10' as const,
      starIdentifier: '00:11:62:00:00:01',
      cashDrawer: true,
      cloudPrntUrl: 'https://cloud.example/prnt',
    };
    const settings = printerSettingsForBridge(printer);
    expect(bridgeSettingsHasRequiredKeys(settings)).toBe(true);
    for (const key of PRINTER_SETTINGS_BRIDGE_KEYS) {
      expect(settings).toHaveProperty(key);
    }
  });
});
