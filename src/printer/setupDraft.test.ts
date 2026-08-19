import {buildConfigFromDraft, emptyDraft} from '../native/posConnect';
import {defaultPrintEngine} from './enginePolicy';

jest.mock('./starSdk', () => ({
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

describe('setup draft → Star engine', () => {
  it('maps brand STAR to STAR_IO10 without extra user choice', () => {
    const draft = emptyDraft();
    draft.brand = 'STAR';
    draft.printEngine = defaultPrintEngine('STAR');
    draft.starIdentifier = '00:11:62:aa:bb:cc';
    draft.divisionUrl = 'https://pos.example/screens/display/1';
    const config = buildConfigFromDraft(draft);
    expect(config.printer.brand).toBe('STAR');
    expect(config.printer.printEngine).toBe('STAR_IO10');
    expect(config.printer.starIdentifier).toBe('00:11:62:aa:bb:cc');
  });

  it('maps USB ids, reconnect, and printer identity from the draft', () => {
    const draft = emptyDraft();
    draft.printerName = 'Kitchen Star';
    draft.printerRole = 'KITCHEN';
    draft.printerEnabled = true;
    draft.printerDeviceName = 'TSP143';
    draft.usbVendorId = 1305;
    draft.usbProductId = 1;
    draft.autoReconnect = false;
    draft.retryCount = 5;
    draft.connection = 'USB';
    const config = buildConfigFromDraft(draft);
    expect(config.printer.name).toBe('Kitchen Star');
    expect(config.printer.role).toBe('KITCHEN');
    expect(config.printer.deviceName).toBe('TSP143');
    expect(config.printer.usbVendorId).toBe(1305);
    expect(config.printer.usbProductId).toBe(1);
    expect(config.printer.autoReconnect).toBe(false);
    expect(config.printer.retryCount).toBe(5);
    expect(config.customer.device).toBe('');
  });
});
