export type PrinterWidthClass = "3inch" | "4inch";
export type PrinterCutMode = "full" | "partial";
export type ConnectionType = "BLUETOOTH" | "BLE" | "LAN" | "USB" | "BUILTIN" | "VENDOR";
export type PrinterBrand =
  | "GENERIC_ESC_POS"
  | "EPSON"
  | "STAR"
  | "XPRINTER"
  | "RONGTA"
  | "GPRINTER";
export type PrintEngine = "STAR_IO10" | "PASSPRNT" | "CLOUDPRNT" | "ESC_POS";
/** StarIO10 InterfaceType names — do not invent UsbVendor (not in StarIO10 1.13). */
export type StarInterface = "Lan" | "Bluetooth" | "BluetoothLE" | "Usb";
export type OrientationMode = "portrait" | "landscape" | "auto";

export interface PrinterProfile {
  widthMm: number;
  printableWidthMm: number;
  charactersPerLine: number;
  dpi: number;
  encoding: string;
  cutSupported: boolean;
  qrSupported: boolean;
  barcodeSupported: boolean;
  cashDrawerSupported: boolean;
  imageSupported: boolean;
}

export interface DivisionConfig {
  name: string;
  code: string;
  url: string;
}

export interface CustomerConfig {
  name: string;
  code: string;
  store: string;
  device: string;
}

export interface PrinterConfig {
  id: string;
  name: string;
  enabled: boolean;
  width: PrinterWidthClass;
  brand: PrinterBrand;
  model: string;
  connection: ConnectionType;
  ip: string;
  port: number;
  macAddress: string;
  usbVendorId: number;
  usbProductId: number;
  deviceName: string;
  autoReconnect: boolean;
  retryCount: number;
  showPrintDialog: boolean;
  autoCut: boolean;
  cutMode: PrinterCutMode;
  printEngine: PrintEngine;
  starIdentifier: string;
  cashDrawer: boolean;
  cloudPrntUrl: string;
  passPrntPort: string;
  passPrntSettings: string;
  profile: PrinterProfile;
}

export interface SecurityConfig {
  allowedDomains: string[];
  allowExternalNavigation: boolean;
  openExternalInSystemBrowser: boolean;
  requireHttps: boolean;
}

export interface AppConfig {
  configVersion: number;
  setupCompleted: boolean;
  orientation: OrientationMode;
  division: DivisionConfig;
  customer: CustomerConfig;
  printer: PrinterConfig;
  printers: PrinterConfig[];
  security: SecurityConfig;
}

export interface PrinterStatus {
  connected: boolean;
  ready: boolean;
  paperOut: boolean;
  coverOpen: boolean;
  offline: boolean;
  error: string | null;
}

export interface PluginResult<T = Record<string, unknown>> {
  success: boolean;
  data?: T;
  errorCode?: string;
  message?: string;
}

export interface DeviceInfo {
  platform: string;
  osVersion: string;
  manufacturer: string;
  model: string;
  appVersion: string;
  screenSize: string;
  webViewVersion: string;
  printerEngineVersion: string;
  printerCapability: string;
}

export interface DiscoveredPrinter {
  name: string;
  identifier: string;
  connectionType: ConnectionType;
  brand: PrinterBrand;
  model?: string;
  manufacturer?: string;
  signalStrength?: number;
  isConnected?: boolean;
  details?: string;
}

export const PRINTER_ERROR_CODES = [
  "PRINTER_NOT_FOUND",
  "PRINTER_OFFLINE",
  "PRINTER_BUSY",
  "PRINTER_PERMISSION_DENIED",
  "BLUETOOTH_DISABLED",
  "BLUETOOTH_PERMISSION_DENIED",
  "USB_PERMISSION_DENIED",
  "NETWORK_ERROR",
  "TIMEOUT",
  "PAPER_OUT",
  "COVER_OPEN",
  "UNSUPPORTED_OPERATION",
  "UNSUPPORTED_PRINTER",
  "INVALID_RECEIPT",
  "UNAUTHORIZED_ORIGIN"
] as const;

export type PrinterErrorCode = (typeof PRINTER_ERROR_CODES)[number];

export function profileForWidth(width: PrinterWidthClass): PrinterProfile {
  if (width === "4inch") {
    return {
      widthMm: 110,
      printableWidthMm: 104,
      charactersPerLine: 64,
      dpi: 203,
      encoding: "UTF-8",
      cutSupported: true,
      qrSupported: true,
      barcodeSupported: true,
      cashDrawerSupported: true,
      imageSupported: true
    };
  }
  return {
    widthMm: 80,
    printableWidthMm: 72,
    charactersPerLine: 48,
    dpi: 203,
    encoding: "UTF-8",
    cutSupported: true,
    qrSupported: true,
    barcodeSupported: true,
    cashDrawerSupported: true,
    imageSupported: true
  };
}

export function emptyConfig(configVersion = 1): AppConfig {
  return {
    configVersion,
    setupCompleted: false,
    orientation: "portrait",
    division: { name: "", code: "", url: "" },
    customer: { name: "", code: "", store: "", device: "" },
    printer: {
      id: "primary_receipt",
      name: "Receipt Printer",
      enabled: true,
      width: "3inch",
      brand: "GENERIC_ESC_POS",
      model: "",
      connection: "LAN",
      ip: "",
      port: 9100,
      macAddress: "",
      usbVendorId: 0,
      usbProductId: 0,
      deviceName: "",
      autoReconnect: true,
      retryCount: 3,
      showPrintDialog: false,
      autoCut: true,
      cutMode: "partial",
      printEngine: "ESC_POS",
      starIdentifier: "",
      cashDrawer: false,
      cloudPrntUrl: "",
      passPrntPort: "",
      passPrntSettings: "",
      profile: profileForWidth("3inch")
    },
    printers: [],
    security: {
      allowedDomains: [],
      allowExternalNavigation: false,
      openExternalInSystemBrowser: true,
      requireHttps: true
    }
  };
}

const URL_RE = /^(https?):\/\/([^/?#\s]+)(\/[^?#\s]*)?(\?[^#\s]*)?(#\S*)?$/i;

export function validatePosUrl(input: string): { ok: boolean; message: string; url: string } {
  const url = input.trim().replace(/\s+/g, "");
  if (!url) {
    return { ok: false, message: "Please enter a valid POS URL.", url };
  }
  const m = URL_RE.exec(url);
  if (!m) {
    return { ok: false, message: "Please enter a valid POS URL.", url };
  }
  const normalized = url.endsWith('/') ? url : url;
  return { ok: true, message: "", url: normalized };
}

export function extractHost(url: string): string | null {
  const m = URL_RE.exec(url.trim());
  return m ? m[2].toLowerCase() : null;
}

export function buildAllowedDomains(url: string, extra: string[] = []): string[] {
  const host = extractHost(url);
  const list = [...extra];
  if (host) {
    list.push(host);
    const parts = host.split(".");
    if (parts.length >= 2) {
      list.push(parts.slice(-2).join("."));
    }
  }
  return [...new Set(list.filter(Boolean))];
}
