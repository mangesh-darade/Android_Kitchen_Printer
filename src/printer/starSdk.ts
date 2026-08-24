import {
  InterfaceType,
  StarConnectionSettings,
  StarDeviceDiscoveryManagerFactory,
  StarIO10Logger,
  StarPrinter,
  StarPrinterModel,
} from 'react-native-star-io10';
import type {
  DiscoveredPrinter,
  PluginResult,
  PrinterConfig,
  PrinterStatus,
  StarInterface,
} from '../core/config/models';
import {connectionToStarInterface, starIdentifierFromPrinter, starInterfaceToConnection} from './enginePolicy';
import {
  buildCutCommands,
  buildDrawerCommands,
  buildImageCommands,
  buildStarCommands,
  buildTestPrintCommands,
  buildTextCommands,
} from './starCommands';
import {enqueueStarJob} from './starPrintQueue';

export type StarDiscoveryOptions = {
  interfaces?: StarInterface[];
  timeoutMs?: number;
};

function interfaceType(iface: StarInterface): InterfaceType {
  switch (iface) {
    case 'Bluetooth':
      return InterfaceType.Bluetooth;
    case 'BluetoothLE':
      return InterfaceType.BluetoothLE;
    case 'Usb':
      return InterfaceType.Usb;
    case 'Lan':
    default:
      return InterfaceType.Lan;
  }
}

function discoveryTimeMs(ifaces: StarInterface[]): number {
  if (ifaces.includes('BluetoothLE')) {
    return 10000;
  }
  if (ifaces.includes('Lan')) {
    return 5000;
  }
  return 2000;
}

function settingsFor(printer: PrinterConfig): StarConnectionSettings {
  const settings = new StarConnectionSettings();
  settings.interfaceType = interfaceType(connectionToStarInterface(printer.connection));
  settings.identifier = starIdentifierFromPrinter(printer);
  settings.autoSwitchInterface = true;
  return settings;
}

async function withPrinter<T>(
  printer: PrinterConfig,
  work: (device: StarPrinter) => Promise<T>,
): Promise<T> {
  const device = new StarPrinter(settingsFor(printer));
  try {
    await device.open();
    return await work(device);
  } finally {
    try {
      await device.close();
    } catch {
      // already closed
    }
    try {
      await device.dispose();
    } catch {
      // native object already released
    }
  }
}

function asPluginError(error: unknown): PluginResult {
  const message = error instanceof Error ? error.message : String(error);
  const lower = message.toLowerCase();
  if (lower.includes('paper')) {
    return {success: false, errorCode: 'PAPER_OUT', message};
  }
  if (lower.includes('cover')) {
    return {success: false, errorCode: 'COVER_OPEN', message};
  }
  if (lower.includes('unprintable') || lower.includes('tsp100')) {
    return {
      success: false,
      errorCode: 'UNSUPPORTED_OPERATION',
      message: `${message} (TSP100III graphics-only models need raster/PassPRNT)`,
    };
  }
  return {success: false, errorCode: 'PRINTER_OFFLINE', message};
}

export async function discoverStarPrinters(
  options: StarDiscoveryOptions = {},
): Promise<DiscoveredPrinter[]> {
  const interfaces = options.interfaces?.length
    ? options.interfaces
    : (['Lan', 'Bluetooth', 'BluetoothLE', 'Usb'] as StarInterface[]);
  const manager = await StarDeviceDiscoveryManagerFactory.create(
    interfaces.map(interfaceType),
  );
  manager.discoveryTime = options.timeoutMs ?? discoveryTimeMs(interfaces);
  const found: DiscoveredPrinter[] = [];
  await new Promise<void>((resolve, reject) => {
    manager.onPrinterFound = device => {
      const iface = device.connectionSettings.interfaceType as StarInterface;
      const identifier = device.connectionSettings.identifier;
      const lan = device.information?.detail?.lan;
      found.push({
        name: String(device.information?.model || 'Star Printer'),
        identifier,
        connectionType: starInterfaceToConnection(iface),
        brand: 'STAR',
        model: device.information?.model,
        manufacturer: 'Star Micronics',
        details: lan?.ipAddress || lan?.macAddress || identifier,
      });
    };
    manager.onDiscoveryFinished = () => resolve();
    manager.startDiscovery().catch(reject);
  });
  try {
    await manager.stopDiscovery();
  } catch {
    // finished already
  }
  return found;
}

export async function printStarText(
  printer: PrinterConfig,
  text: string,
  extra?: {bold?: boolean; qr?: string; barcode?: string},
): Promise<PluginResult> {
  return enqueueStarJob(async () => {
    try {
      await withPrinter(printer, async device => {
        warnIfGraphicsOnly(device);
        const commands = await buildTextCommands(text, {
          bold: extra?.bold,
          qr: extra?.qr,
          barcode: extra?.barcode,
          autoCut: printer.autoCut,
          cutMode: printer.cutMode,
          width: printer.width,
        });
        await device.print(commands);
      });
      return {success: true, message: 'Printed via StarIO10'};
    } catch (error) {
      return asPluginError(error);
    }
  });
}

export async function starTestPrint(printer: PrinterConfig): Promise<PluginResult> {
  return enqueueStarJob(async () => {
    try {
      await withPrinter(printer, async device => {
        warnIfGraphicsOnly(device);
        const commands = await buildTestPrintCommands(printer.width, printer.cutMode);
        await device.print(commands);
      });
      return {success: true, message: 'StarIO10 test print sent'};
    } catch (error) {
      return asPluginError(error);
    }
  });
}

export async function starCutPaper(printer: PrinterConfig): Promise<PluginResult> {
  return enqueueStarJob(async () => {
    try {
      await withPrinter(printer, async device => {
        await device.print(await buildCutCommands(printer.cutMode));
      });
      return {success: true, message: 'Cut sent'};
    } catch (error) {
      return asPluginError(error);
    }
  });
}

export async function starOpenDrawer(printer: PrinterConfig): Promise<PluginResult> {
  return enqueueStarJob(async () => {
    try {
      await withPrinter(printer, async device => {
        await device.print(await buildDrawerCommands());
      });
      return {success: true, message: 'Drawer pulse sent'};
    } catch (error) {
      return asPluginError(error);
    }
  });
}

export async function starGetStatus(printer: PrinterConfig): Promise<PrinterStatus> {
  return enqueueStarJob(async () => {
    try {
      return await withPrinter(printer, async device => {
        const status = await device.getStatus();
        return {
          connected: true,
          ready: !status.hasError && !status.paperEmpty && !status.coverOpen,
          paperOut: status.paperEmpty,
          coverOpen: status.coverOpen,
          offline: status.hasError,
          error: status.hasError ? 'Star printer reported an error' : null,
        };
      });
    } catch (error) {
      return {
        connected: false,
        ready: false,
        paperOut: false,
        coverOpen: false,
        offline: true,
        error: error instanceof Error ? error.message : 'Status failed',
      };
    }
  });
}

export async function starGetInformation(
  printer: PrinterConfig,
): Promise<PluginResult<Record<string, string>>> {
  return enqueueStarJob(async () => {
    try {
      return await withPrinter(printer, async device => {
        const info = device.information;
        return {
          success: true,
          data: {
            model: String(info?.model ?? StarPrinterModel.Unknown),
            emulation: String(info?.emulation ?? ''),
            identifier: device.connectionSettings.identifier,
            interfaceType: String(device.connectionSettings.interfaceType),
          },
        };
      });
    } catch (error) {
      return asPluginError(error);
    }
  });
}

export async function printStarImage(
  printer: PrinterConfig,
  imagePath: string,
): Promise<PluginResult> {
  return enqueueStarJob(async () => {
    try {
      await withPrinter(printer, async device => {
        const commands = await buildImageCommands(
          imagePath,
          printer.width,
          printer.cutMode,
          printer.autoCut,
        );
        await device.print(commands);
      });
      return {success: true, message: 'Image printed via StarIO10'};
    } catch (error) {
      return asPluginError(error);
    }
  });
}

export async function starGetDefaultConfiguration(
  printer: PrinterConfig,
): Promise<PluginResult<string>> {
  return enqueueStarJob(async () => {
    try {
      const xml = await withPrinter(printer, device => device.getDefaultStarConfiguration());
      return {success: true, data: xml as unknown as string, message: 'Default Star Configuration'};
    } catch (error) {
      return asPluginError(error);
    }
  });
}

export async function starSetConfiguration(
  printer: PrinterConfig,
  xml: string,
): Promise<PluginResult> {
  return enqueueStarJob(async () => {
    try {
      await withPrinter(printer, device => device.setStarConfiguration(xml));
      return {success: true, message: 'Star Configuration applied'};
    } catch (error) {
      return asPluginError(error);
    }
  });
}

export async function starGetConfiguration(printer: PrinterConfig): Promise<PluginResult<string>> {
  return enqueueStarJob(async () => {
    try {
      const xml = await withPrinter(printer, device => device.getStarConfiguration());
      return {success: true, data: xml as unknown as string, message: 'Star Configuration'};
    } catch (error) {
      return asPluginError(error);
    }
  });
}

export async function setStarIo10Logging(enabled: boolean): Promise<void> {
  if (enabled) {
    await StarIO10Logger.instance.start();
  } else {
    await StarIO10Logger.instance.stop();
  }
}

export async function printStarCommands(
  printer: PrinterConfig,
  commands: string,
): Promise<PluginResult> {
  return enqueueStarJob(async () => {
    try {
      await withPrinter(printer, device => device.print(commands));
      return {success: true, message: 'StarXpand commands sent'};
    } catch (error) {
      return asPluginError(error);
    }
  });
}

function warnIfGraphicsOnly(device: StarPrinter) {
  const model = String(device.information?.model || '');
  if (model.includes('TSP100') && model.includes('III')) {
    // TSP100III cannot actionPrintText on some SKUs; caller still attempts print
    // and asPluginError maps unprintable errors.
  }
}

export {buildStarCommands};

