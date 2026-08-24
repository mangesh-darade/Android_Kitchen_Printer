import {StarXpandCommand} from 'react-native-star-io10';
import type {PrinterCutMode, PrinterWidthClass} from '../core/config/models';
import {printableAreaMm} from './enginePolicy';

export type StarJobKind = 'text' | 'qr' | 'barcode' | 'cut' | 'drawer' | 'test';

export type StarCommandOptions = {
  text?: string;
  bold?: boolean;
  autoCut?: boolean;
  cutMode?: PrinterCutMode;
  width?: PrinterWidthClass;
  qr?: string;
  barcode?: string;
  feedLines?: number;
  imagePath?: string;
  imageWidthDots?: number;
};

function cutType(mode: PrinterCutMode | undefined) {
  return mode === 'full'
    ? StarXpandCommand.Printer.CutType.Full
    : StarXpandCommand.Printer.CutType.Partial;
}

function printerBuilder(options: StarCommandOptions): StarXpandCommand.PrinterBuilder {
  const printer = new StarXpandCommand.PrinterBuilder();
  if (options.bold) {
    printer.styleBold(true);
  }
  if (options.text) {
    const body = options.text.endsWith('\n') ? options.text : `${options.text}\n`;
    printer.actionPrintText(body);
  }
  if (options.qr) {
    printer.actionPrintQRCode(
      new StarXpandCommand.Printer.QRCodeParameter(options.qr).setCellSize(4),
    );
  }
  if (options.barcode) {
    printer.actionPrintBarcode(
      new StarXpandCommand.Printer.BarcodeParameter(
        options.barcode,
        StarXpandCommand.Printer.BarcodeSymbology.Code128,
      ).setPrintHri(true).setHeight(12),
    );
  }
  if (options.imagePath) {
    printer.actionPrintImage(
      new StarXpandCommand.Printer.ImageParameter(
        options.imagePath,
        options.imageWidthDots ?? 576,
      ),
    );
  }
  printer.actionFeedLine(options.feedLines ?? 2);
  if (options.autoCut !== false && options.cutMode) {
    printer.actionCut(cutType(options.cutMode));
  } else if (options.autoCut) {
    printer.actionCut(StarXpandCommand.Printer.CutType.Partial);
  }
  return printer;
}

function documentFor(options: StarCommandOptions): StarXpandCommand.DocumentBuilder {
  const document = new StarXpandCommand.DocumentBuilder();
  if (options.width) {
    document.settingPrintableArea(printableAreaMm(options.width));
  }
  document.addPrinter(printerBuilder(options));
  return document;
}

export async function buildStarCommands(options: StarCommandOptions): Promise<string> {
  const builder = new StarXpandCommand.StarXpandCommandBuilder();
  builder.addDocument(documentFor(options));
  return builder.getCommands();
}

export async function buildTextCommands(
  text: string,
  options: Omit<StarCommandOptions, 'text'> = {},
): Promise<string> {
  return buildStarCommands({...options, text});
}

export async function buildTestPrintCommands(
  width: PrinterWidthClass,
  cutMode: PrinterCutMode,
): Promise<string> {
  return buildStarCommands({
    text:
      'POS Connect / StarIO10\n' +
      'StarXpand SDK test print\n' +
      `Paper: ${width}\n` +
      `Cut: ${cutMode}\n`,
    autoCut: true,
    cutMode,
    width,
  });
}

export async function buildCutCommands(cutMode: PrinterCutMode): Promise<string> {
  const printer = new StarXpandCommand.PrinterBuilder()
    .actionFeedLine(1)
    .actionCut(cutType(cutMode));
  const builder = new StarXpandCommand.StarXpandCommandBuilder();
  builder.addDocument(new StarXpandCommand.DocumentBuilder().addPrinter(printer));
  return builder.getCommands();
}

export async function buildImageCommands(
  imagePath: string,
  width: PrinterWidthClass,
  cutMode: PrinterCutMode,
  autoCut: boolean,
): Promise<string> {
  return buildStarCommands({
    imagePath,
    imageWidthDots: width === '4inch' ? 832 : 576,
    autoCut,
    cutMode,
    width,
    feedLines: 1,
  });
}

export async function buildDrawerCommands(): Promise<string> {
  const builder = new StarXpandCommand.StarXpandCommandBuilder();
  builder.addDocument(
    new StarXpandCommand.DocumentBuilder().addDrawer(
      new StarXpandCommand.DrawerBuilder().actionOpen(
        new StarXpandCommand.Drawer.OpenParameter(),
      ),
    ),
  );
  return builder.getCommands();
}
