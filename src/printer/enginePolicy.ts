import type {
  ConnectionType,
  PrintEngine,
  PrinterBrand,
  PrinterConfig,
  StarInterface,
} from '../core/config/models';

export const PRINT_ENGINES: PrintEngine[] = [
  'STAR_IO10',
  'PASSPRNT',
  'CLOUDPRNT',
  'ESC_POS',
];

export const PRINTER_BRANDS: PrinterBrand[] = [
  'GENERIC_ESC_POS',
  'STAR',
  'EPSON',
  'SUNMI',
  'XPRINTER',
  'RONGTA',
  'GPRINTER',
  'CUSTOM',
];

/** Brand STAR defaults to official StarIO10. All other brands keep existing ESC/POS LAN/BT/USB. */
export function defaultPrintEngine(brand: PrinterBrand): PrintEngine {
  return brand === 'STAR' ? 'STAR_IO10' : 'ESC_POS';
}

export function usesExistingEscPosStack(engine: PrintEngine): boolean {
  return engine === 'ESC_POS';
}

export function usesStarIo10Sdk(engine: PrintEngine): boolean {
  return engine === 'STAR_IO10';
}

export function connectionToStarInterface(connection: ConnectionType): StarInterface {
  switch (connection) {
    case 'BLUETOOTH':
      return 'Bluetooth';
    case 'BLE':
      return 'BluetoothLE';
    case 'USB':
      return 'Usb';
    case 'LAN':
    case 'BUILTIN':
    case 'VENDOR':
    default:
      return 'Lan';
  }
}

export function starInterfaceToConnection(iface: StarInterface): ConnectionType {
  switch (iface) {
    case 'Bluetooth':
      return 'BLUETOOTH';
    case 'BluetoothLE':
      return 'BLE';
    case 'Usb':
      return 'USB';
    case 'Lan':
    default:
      return 'LAN';
  }
}

export function starIdentifierFromPrinter(printer: PrinterConfig): string {
  const id = printer.starIdentifier.trim();
  if (id) {
    return id;
  }
  if (printer.macAddress.trim()) {
    return printer.macAddress.trim();
  }
  return printer.ip.trim();
}

export function passPrntDotWidth(width: PrinterConfig['width']): number {
  return width === '4inch' ? 832 : 576;
}

export function printableAreaMm(width: PrinterConfig['width']): number {
  return width === '4inch' ? 104 : 72;
}
