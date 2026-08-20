import {
  catalogEntryForBrand,
  printerSdkSettingsFields,
  resolveActiveSdkTechName,
  resolveSdkIntegrationStatus,
  resolveSdkPrintPath,
  VENDOR_SDK_CATALOG,
} from './vendorSdkCatalog';

describe('vendorSdkCatalog', () => {
  it('lists every printer brand with an official SDK reference', () => {
    const brands = VENDOR_SDK_CATALOG.map(item => item.brand);
    expect(brands).toEqual([
      'GENERIC_ESC_POS',
      'EPSON',
      'STAR',
      'SUNMI',
      'XPRINTER',
      'RONGTA',
      'GPRINTER',
      'CUSTOM',
    ]);
  });

  it('resolves StarIO10 tech name for Star brand', () => {
    expect(resolveActiveSdkTechName('STAR', 'STAR_IO10', 'LAN')).toContain('StarIO10');
    expect(resolveActiveSdkTechName('STAR', 'PASSPRNT', 'LAN')).toContain('PassPRNT');
    expect(resolveActiveSdkTechName('STAR', 'ESC_POS', 'LAN')).toContain('ESC/POS');
  });

  it('marks Epson as integrated when official SDK is bundled', () => {
    expect(resolveSdkIntegrationStatus('EPSON', 'ESC_POS', 'LAN')).toBe('integrated');
    expect(resolveActiveSdkTechName('EPSON', 'ESC_POS', 'LAN')).toContain('Epson ePOS SDK');
  });

  it('marks SUNMI built-in as integrated via Maven library', () => {
    expect(resolveSdkIntegrationStatus('SUNMI', 'ESC_POS', 'BUILTIN')).toBe('integrated');
    expect(resolveActiveSdkTechName('SUNMI', 'ESC_POS', 'BUILTIN')).toContain('SUNMI PrinterLibrary');
  });

  it('exposes sdk fields for printer settings bridge', () => {
    const fields = printerSdkSettingsFields('XPRINTER', 'ESC_POS', 'LAN');
    expect(fields.sdkTechName).toContain('XPrinter');
    expect(fields.sdkIntegrated).toBe(true);
    expect(fields.sdkPrintPath).toBe('vendor_sdk');
    expect(fields.sdkUsesVendorApi).toBe(true);
    expect(catalogEntryForBrand('XPRINTER').downloadUrl).toContain('xprintertech.com');
  });

  it('marks Rongta as ESC/POS fallback until SDK is bundled', () => {
    expect(resolveSdkPrintPath('RONGTA', 'ESC_POS', 'LAN')).toBe('escpos_fallback');
    expect(resolveSdkIntegrationStatus('RONGTA', 'ESC_POS', 'LAN')).toBe('fallback');
  });
});
