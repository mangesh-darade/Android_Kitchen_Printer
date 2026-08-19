import type {PluginResult, PrinterConfig} from '../core/config/models';
import type {printWithCloudPrnt} from './cloudPrnt';
import {usesExistingEscPosStack} from './enginePolicy';
import type {printWithPassPrnt} from './passPrnt';
import type {
  printStarImage,
  printStarText,
  starCutPaper,
  starOpenDrawer,
  starTestPrint,
} from './starSdk';

export type RoutedPrintAction =
  | 'printText'
  | 'printImage'
  | 'testPrint'
  | 'cutPaper'
  | 'openDrawer'
  | 'printQR'
  | 'printBarcode';

export type RoutedPrintJob = {
  action: RoutedPrintAction;
  printer: PrinterConfig;
  text?: string;
  bold?: boolean;
  qr?: string;
  barcode?: string;
  imagePath?: string;
  imageBase64?: string;
};

export type EscPosNative = (job: RoutedPrintJob) => Promise<PluginResult>;

export type PrintRouterDeps = {
  escPos: EscPosNative;
  starText?: typeof printStarText;
  starTest?: typeof starTestPrint;
  starCut?: typeof starCutPaper;
  starDrawer?: typeof starOpenDrawer;
  starImage?: typeof printStarImage;
  passPrnt?: typeof printWithPassPrnt;
  cloudPrnt?: typeof printWithCloudPrnt;
};

/** STAR_IO10 / PassPRNT / CloudPRNT must not open existing ESC/POS TcpTransport. */
export function shouldSkipEscPosTransport(engine: PrinterConfig['printEngine']): boolean {
  return engine !== 'ESC_POS';
}

export function selectedEngine(printer: PrinterConfig): PrinterConfig['printEngine'] {
  return printer.printEngine;
}

export async function routePrint(
  job: RoutedPrintJob,
  deps: Partial<PrintRouterDeps> = {},
): Promise<PluginResult> {
  const started = Date.now();
  const engine = job.printer.printEngine;
  let result: PluginResult;

  switch (engine) {
    case 'PASSPRNT': {
      const passPrnt = deps.passPrnt ?? require('./passPrnt').printWithPassPrnt;
      result = await passPrnt({
        text: jobText(job),
        printer: job.printer,
        openDrawer: job.action === 'openDrawer' || job.printer.cashDrawer,
        imageBase64: job.imageBase64,
      });
      break;
    }
    case 'CLOUDPRNT': {
      const cloudPrnt = deps.cloudPrnt ?? require('./cloudPrnt').printWithCloudPrnt;
      result = await cloudPrnt({
        text: jobText(job),
        printer: job.printer,
        imageBase64: job.imageBase64,
      });
      break;
    }
    case 'STAR_IO10':
      result = await routeStar(job, deps);
      break;
    case 'ESC_POS':
    default:
      if (engine !== 'ESC_POS' && !usesExistingEscPosStack(engine)) {
        result = {success: false, errorCode: 'UNSUPPORTED_OPERATION', message: 'Unknown engine'};
        break;
      }
      result = await (deps.escPos
        ? deps.escPos(job)
        : Promise.resolve({
            success: false,
            errorCode: 'UNSUPPORTED_OPERATION',
            message: 'ESC/POS native handler was not provided',
          }));
      break;
  }

  return {
    ...result,
    data: {
      ...(typeof result.data === 'object' && result.data ? result.data : {}),
      engine,
      durationMs: Date.now() - started,
    },
  };
}

async function routeStar(job: RoutedPrintJob, deps: Partial<PrintRouterDeps>): Promise<PluginResult> {
  const star =
    deps.starText || deps.starTest || deps.starCut || deps.starDrawer || deps.starImage
      ? deps
      : require('./starSdk');
  switch (job.action) {
    case 'testPrint':
      return (deps.starTest ?? star.starTestPrint)(job.printer);
    case 'cutPaper':
      return (deps.starCut ?? star.starCutPaper)(job.printer);
    case 'openDrawer':
      return (deps.starDrawer ?? star.starOpenDrawer)(job.printer);
    case 'printQR':
      return (deps.starText ?? star.printStarText)(job.printer, job.text || '', {qr: job.qr});
    case 'printBarcode':
      return (deps.starText ?? star.printStarText)(job.printer, job.text || '', {barcode: job.barcode});
    case 'printImage': {
      const path = job.imagePath?.trim() || '';
      if (!path) {
        return {success: false, errorCode: 'INVALID_RECEIPT', message: 'Star image file path is required'};
      }
      const printImage = deps.starImage ?? star.printStarImage;
      if (typeof printImage !== 'function') {
        return {success: false, errorCode: 'UNSUPPORTED_OPERATION', message: 'Star image printer missing'};
      }
      return printImage(job.printer, path);
    }
    case 'printText':
    default:
      return (deps.starText ?? star.printStarText)(job.printer, job.text || '', {bold: job.bold});
  }
}

function jobText(job: RoutedPrintJob): string {
  if (job.text) {
    return job.text;
  }
  if (job.qr) {
    return job.qr;
  }
  if (job.barcode) {
    return job.barcode;
  }
  if (job.action === 'testPrint') {
    return 'POS Connect Star test print';
  }
  return '';
}
