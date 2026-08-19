import {emptyConfig} from './models';
import {migrateConfig} from './migration';

describe('config migration', () => {
  it('fills Star fields on v1 configs without dropping LAN ip', () => {
    const migrated = migrateConfig({
      configVersion: 1,
      setupCompleted: true,
      printer: {
        brand: 'STAR',
        ip: '192.168.1.50',
        port: 9100,
        connection: 'LAN',
        width: '3inch',
      },
    });
    expect(migrated.configVersion).toBe(2);
    expect(migrated.printer.ip).toBe('192.168.1.50');
    expect(migrated.printer.printEngine).toBe('STAR_IO10');
    expect(migrated.printer.starIdentifier).toBe('192.168.1.50');
    expect(migrated.printer.cashDrawer).toBe(false);
    expect(migrated.printer.cloudPrntUrl).toBe('');
  });

  it('keeps ESC_POS for generic brands', () => {
    const migrated = migrateConfig({
      printer: {brand: 'GENERIC_ESC_POS', ip: '10.0.0.8'},
    });
    expect(migrated.printer.printEngine).toBe('ESC_POS');
  });

  it('allows Star brand to stay on ESC_POS when explicitly set (LAN 9100 fallback)', () => {
    const migrated = migrateConfig({
      printer: {brand: 'STAR', printEngine: 'ESC_POS', ip: '192.168.0.20'},
    });
    expect(migrated.printer.printEngine).toBe('ESC_POS');
  });

  it('emptyConfig has ESC_POS so existing LAN path stays default', () => {
    expect(emptyConfig().printer.printEngine).toBe('ESC_POS');
    expect(emptyConfig().printer.connection).toBe('LAN');
  });
});
