import {defaultPrintEngine, usesExistingEscPosStack, connectionToStarInterface} from './enginePolicy';
import {shouldSkipEscPosTransport} from './printRouter';

describe('enginePolicy', () => {
  it('defaults Star brand to STAR_IO10 and others to ESC_POS', () => {
    expect(defaultPrintEngine('STAR')).toBe('STAR_IO10');
    expect(defaultPrintEngine('GENERIC_ESC_POS')).toBe('ESC_POS');
    expect(defaultPrintEngine('EPSON')).toBe('ESC_POS');
  });

  it('keeps existing ESC/POS LAN stack only for ESC_POS engine', () => {
    expect(usesExistingEscPosStack('ESC_POS')).toBe(true);
    expect(usesExistingEscPosStack('STAR_IO10')).toBe(false);
    expect(usesExistingEscPosStack('PASSPRNT')).toBe(false);
    expect(usesExistingEscPosStack('CLOUDPRNT')).toBe(false);
  });

  it('maps connection to official StarIO10 InterfaceType names', () => {
    expect(connectionToStarInterface('LAN')).toBe('Lan');
    expect(connectionToStarInterface('BLUETOOTH')).toBe('Bluetooth');
    expect(connectionToStarInterface('BLE')).toBe('BluetoothLE');
    expect(connectionToStarInterface('USB')).toBe('Usb');
  });

  it('does not open ESC/POS TCP when using a Star engine', () => {
    expect(shouldSkipEscPosTransport('STAR_IO10')).toBe(true);
    expect(shouldSkipEscPosTransport('ESC_POS')).toBe(false);
  });
});
