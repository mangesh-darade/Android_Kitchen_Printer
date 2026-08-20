import {NativeModules} from 'react-native';
import {emptyConfig, type AppConfig} from '../core/config/models';
import {
  printerDisabledResult,
  runPrinterAction,
  testPrinter,
} from './posConnect';

const mockRoutePrint = jest.fn();

jest.mock('../printer/printRouter', () => ({
  routePrint: (...args: unknown[]) => mockRoutePrint(...args),
}));

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

function starConfig(overrides: Partial<AppConfig['printer']> = {}): AppConfig {
  return {
    ...emptyConfig(),
    printer: {
      ...emptyConfig().printer,
      printEngine: 'STAR_IO10',
      starIdentifier: '192.168.1.50',
      enabled: true,
      ...overrides,
    },
  };
}

describe('printer guard — enabled / disabled', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    NativeModules.PosConnect = {
      getConfiguration: jest.fn(async () => JSON.stringify(starConfig())),
      notifyPrintResult: jest.fn(),
      addListener: jest.fn(),
      removeListeners: jest.fn(),
      testPrinter: jest.fn(async () =>
        JSON.stringify({success: true, message: 'ESC/POS test ok'}),
      ),
    };
  });

  it('printerDisabledResult matches native bridge error shape', () => {
    expect(printerDisabledResult()).toEqual({
      success: false,
      errorCode: 'PRINTER_OFFLINE',
      message: 'Printer is disabled',
    });
  });

  it('testPrinter skips Star route when printer is disabled', async () => {
    const cfg = starConfig({enabled: false});
    const result = await testPrinter(cfg);
    expect(result).toEqual(printerDisabledResult());
    expect(mockRoutePrint).not.toHaveBeenCalled();
  });

  it('testPrinter routes Star jobs when printer is enabled', async () => {
    mockRoutePrint.mockResolvedValue({success: true, message: 'Star test ok'});
    const cfg = starConfig({enabled: true});
    const result = await testPrinter(cfg);
    expect(mockRoutePrint).toHaveBeenCalledWith(
      expect.objectContaining({action: 'testPrint', printer: cfg.printer}),
    );
    expect(result.success).toBe(true);
  });

  it('runPrinterAction blocks all Star actions when disabled', async () => {
    const printer = starConfig({enabled: false}).printer;
    const result = await runPrinterAction('printText', printer, {text: 'KOT'});
    expect(result).toEqual(printerDisabledResult());
    expect(mockRoutePrint).not.toHaveBeenCalled();
  });
});
