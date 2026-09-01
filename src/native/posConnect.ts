import {NativeEventEmitter, NativeModules} from 'react-native';
import {
  AppConfig,
  buildAllowedDomains,
  emptyConfig,
  profileForWidth,
  type ConnectionType,
  type DiscoveredPrinter,
  type PluginResult,
  type PrintEngine,
  type PrinterBrand,
  type PrinterStatus,
} from '../core/config/models';
import {migrateConfig} from '../core/config/migration';
import {parsePrintPayload} from '../bridge/kitchenPrint';
import {
  connectionToStarInterface,
  defaultPrintEngine,
  starIdentifierFromPrinter,
  usesExistingEscPosStack,
} from '../printer/enginePolicy';
import {routePrint, type RoutedPrintAction} from '../printer/printRouter';
import {stripImageBase64} from '../printer/imagePayload';
import {discoverStarPrinters, starGetInformation, starGetStatus} from '../printer/starSdk';

type PosConnectNative = {
  getConfiguration(): Promise<string>;
  saveConfiguration(configJson: string): Promise<string>;
  resetApplication(): Promise<string>;
  resetPrinter(): Promise<string>;
  getDeviceInfo(): Promise<string>;
  discoverPrinters(connection: string): Promise<string>;
  connectPrinter(printerJson: string | null): Promise<string>;
  testPrinter(): Promise<string>;
  checkUrlReachable(url: string): Promise<string>;
  exportLogs(): Promise<string>;
  notifyPrintResult(jobId: string, success: boolean, message: string): Promise<string>;
  writeTempImage(base64: string): Promise<string>;
};

const Native: PosConnectNative | undefined = NativeModules.PosConnect;

export function printerDisabledResult(): PluginResult {
  return {
    success: false,
    errorCode: 'PRINTER_OFFLINE',
    message: 'Printer is disabled',
  };
}

function parseResult<T>(raw: string): PluginResult<T> {
  try {
    return JSON.parse(raw) as PluginResult<T>;
  } catch {
    return {success: false, message: 'Invalid native response'};
  }
}

export async function loadConfig(): Promise<AppConfig> {
  if (!Native) {
    return emptyConfig();
  }
  const raw = await Native.getConfiguration();
  try {
    return migrateConfig(JSON.parse(raw));
  } catch {
    return emptyConfig();
  }
}

export async function saveConfig(config: AppConfig): Promise<PluginResult> {
  if (!Native) {
    return {success: false, message: 'Native module missing'};
  }
  const raw = await Native.saveConfiguration(JSON.stringify(config));
  return parseResult(raw);
}

export async function resetApplication(): Promise<void> {
  await Native?.resetApplication();
}

export async function resetPrinter(): Promise<void> {
  await Native?.resetPrinter();
}

export async function getDeviceInfo(): Promise<PluginResult<Record<string, string>>> {
  if (!Native) {
    return {success: false, message: 'Native module missing'};
  }
  return parseResult(await Native.getDeviceInfo());
}

export async function discoverPrinters(
  connection: ConnectionType,
  config?: AppConfig,
): Promise<DiscoveredPrinter[]> {
  const cfg = config || (await loadConfig());
  if (!usesExistingEscPosStack(cfg.printer.printEngine)) {
    const iface = connectionToStarInterface(connection);
    return discoverStarPrinters({interfaces: [iface]});
  }
  if (!Native) {
    return [];
  }
  const raw = await Native.discoverPrinters(connection);
  const result = parseResult<{printers: DiscoveredPrinter[]}>(raw);
  return result.data?.printers || [];
}

export async function connectPrinter(printerJson?: string): Promise<PluginResult> {
  if (!Native) {
    return {success: false, message: 'Native module missing'};
  }
  const raw = await Native.connectPrinter(printerJson || null);
  return parseResult(raw);
}

export async function testPrinter(config?: AppConfig): Promise<PluginResult> {
  const cfg = config || (await loadConfig());
  if (!cfg.printer.enabled) {
    return printerDisabledResult();
  }
  if (!usesExistingEscPosStack(cfg.printer.printEngine)) {
    return routePrint({action: 'testPrint', printer: cfg.printer});
  }
  if (!Native) {
    return {success: false, message: 'Native module missing'};
  }
  return parseResult(await Native.testPrinter());
}



export async function checkUrlReachable(url: string): Promise<boolean> {
  if (!Native) {
    return false;
  }
  try {
    const raw = await Promise.race([
      Native.checkUrlReachable(url),
      new Promise<string>((_, reject) => {
        setTimeout(() => reject(new Error('URL check timed out')), 12000);
      }),
    ]);
    const result = parseResult<{reachable: boolean}>(raw);
    return !!result.data?.reachable;
  } catch {
    return false;
  }
}

export async function exportLogs(): Promise<string> {
  if (!Native) {
    return '';
  }
  const raw = await Native.exportLogs();
  const result = parseResult<{text: string}>(raw);
  return result.data?.text || '';
}

export async function notifyPrintResult(
  jobId: string,
  success: boolean,
  message: string,
): Promise<void> {
  await Native?.notifyPrintResult(jobId, success, message);
}

export async function writeTempPrintImage(base64: string): Promise<string> {
  if (!Native?.writeTempImage) {
    throw new Error('Native writeTempImage is missing');
  }
  const raw = await Native.writeTempImage(stripImageBase64(base64));
  const result = parseResult<{path: string}>(raw);
  if (!result.success || !result.data?.path) {
    throw new Error(result.message || 'Could not write print image');
  }
  return result.data.path;
}

export async function runPrinterAction(
  action: RoutedPrintAction,
  printer: AppConfig['printer'],
  extra: {text?: string; qr?: string; barcode?: string; imagePath?: string; imageBase64?: string} = {},
): Promise<PluginResult> {
  if (!printer.enabled) {
    return printerDisabledResult();
  }
  if (usesExistingEscPosStack(printer.printEngine)) {
    if (action === 'testPrint') {
      return testPrinter({...emptyConfig(), printer});
    }
    return {success: false, message: `ESC/POS ${action} is handled by the native printer manager`};
  }
  return routePrint({action, printer, ...extra});
}

export async function getStarStatus(printer: AppConfig['printer']): Promise<PrinterStatus> {
  return starGetStatus(printer);
}

export async function getStarInfo(printer: AppConfig['printer']) {
  return starGetInformation(printer);
}

export function buildConfigFromDraft(draft: SetupDraft): AppConfig {
  const base = emptyConfig();
  const brand = draft.brand;
  const printEngine = draft.printEngine || defaultPrintEngine(brand);
  return {
    ...base,
    setupCompleted: true,
    division: {
      url: draft.divisionUrl.trim(),
    },
    printer: {
      ...base.printer,
      brand,
      model: draft.model.trim(),
      width: draft.width,
      connection: draft.connection,
      ip: draft.printerIp.trim(),
      port: draft.printerPort || 9100,
      macAddress: draft.macAddress.trim(),
      starIdentifier: draft.starIdentifier.trim() || draft.macAddress.trim() || draft.printerIp.trim(),
      showPrintDialog: draft.showPrintDialog,
      autoCut: draft.autoCut,
      cutMode: draft.cutMode,
      printEngine,
      name: draft.printerName.trim() || 'Receipt Printer',
      enabled: draft.printerEnabled,
      deviceName: draft.printerDeviceName.trim(),
      usbVendorId: draft.usbVendorId,
      usbProductId: draft.usbProductId,
      autoReconnect: draft.autoReconnect,
      retryCount: draft.retryCount,
      feedLinesTop: draft.feedLinesTop ?? 0,
      feedLinesBottom: draft.feedLinesBottom ?? 2,
      profile: profileForWidth(draft.width),
    },
    security: {
      ...base.security,
      allowedDomains: buildAllowedDomains(draft.divisionUrl.trim()),
    },
  };
}

export type SetupDraft = {
  divisionUrl: string;
  width: '3inch' | '4inch';
  connection: ConnectionType;
  printerIp: string;
  printerPort: number;
  macAddress: string;
  showPrintDialog: boolean;
  autoCut: boolean;
  cutMode: 'full' | 'partial';
  brand: PrinterBrand;
  model: string;
  printEngine: PrintEngine;
  starIdentifier: string;
  printerName: string;
  printerEnabled: boolean;
  printerDeviceName: string;
  usbVendorId: number;
  usbProductId: number;
  autoReconnect: boolean;
  retryCount: number;
  feedLinesTop: number;
  feedLinesBottom: number;
};

export function emptyDraft(): SetupDraft {
  return {
    divisionUrl: '',
    width: '3inch',
    connection: 'LAN',
    printerIp: '',
    printerPort: 9100,
    macAddress: '',
    showPrintDialog: false,
    autoCut: true,
    cutMode: 'partial',
    brand: 'GENERIC_ESC_POS',
    model: '',
    printEngine: 'ESC_POS',
    starIdentifier: '',
    printerName: 'Receipt Printer',
    printerEnabled: true,
    printerDeviceName: '',
    usbVendorId: 0,
    usbProductId: 0,
    autoReconnect: true,
    retryCount: 3,
    feedLinesTop: 0,
    feedLinesBottom: 2,
  };
}

export function draftFromConfig(config: AppConfig): SetupDraft {
  return {
    divisionUrl: config.division.url,
    width: config.printer.width,
    connection: config.printer.connection,
    printerIp: config.printer.ip,
    printerPort: config.printer.port,
    macAddress: config.printer.macAddress,
    showPrintDialog: config.printer.showPrintDialog,
    autoCut: config.printer.autoCut,
    cutMode: config.printer.cutMode,
    brand: config.printer.brand,
    model: config.printer.model,
    printEngine: config.printer.printEngine,
    starIdentifier: starIdentifierFromPrinter(config.printer),
    printerName: config.printer.name,
    printerEnabled: config.printer.enabled,
    printerDeviceName: config.printer.deviceName,
    usbVendorId: config.printer.usbVendorId,
    usbProductId: config.printer.usbProductId,
    autoReconnect: config.printer.autoReconnect,
    retryCount: config.printer.retryCount,
    feedLinesTop: config.printer.feedLinesTop ?? 0,
    feedLinesBottom: config.printer.feedLinesBottom ?? 2,
  };
}

export function startStarPrintListener(): () => void {
  if (!Native) {
    return () => undefined;
  }
  const emitter = new NativeEventEmitter(NativeModules.PosConnect);
  const sub = emitter.addListener('StarPrintRequest', (payload: StarPrintPayload) => {
    void handleStarPrintEvent(payload);
  });
  return () => sub.remove();
}

type StarPrintPayload = {
  jobId: string;
  action: RoutedPrintAction;
  text?: string;
  qr?: string;
  barcode?: string;
  image?: string;
  imageBase64?: string;
};

async function handleStarPrintEvent(payload: StarPrintPayload) {
  try {
    const config = await loadConfig();
    if (!config.printer.enabled) {
      await notifyPrintResult(payload.jobId, false, 'Printer is disabled');
      return;
    }
    const action = payload.action || 'printText';
    const imageBase64 =
      payload.imageBase64 || payload.image || (action === 'printImage' ? payload.text : undefined);
    let imagePath = '';
    if (action === 'printImage' && config.printer.printEngine === 'STAR_IO10') {
      imagePath = await writeTempPrintImage(imageBase64 || '');
    }
    const result = await routePrint({
      action,
      printer: config.printer,
      text: action === 'printImage' ? undefined : parsePrintPayload(payload.text),
      qr: payload.qr,
      barcode: payload.barcode,
      imagePath,
      imageBase64,
    });
    await notifyPrintResult(payload.jobId, !!result.success, result.message || '');
  } catch (error) {
    await notifyPrintResult(
      payload.jobId,
      false,
      error instanceof Error ? error.message : 'Star print failed',
    );
  }
}

export {defaultPrintEngine};
