import {emptyConfig} from '../core/config/models';
import {routePrint, shouldSkipEscPosTransport} from './printRouter';

function starPrinter() {
  const printer = emptyConfig().printer;
  return {
    ...printer,
    brand: 'STAR' as const,
    printEngine: 'STAR_IO10' as const,
    starIdentifier: '00:11:62:00:00:01',
  };
}

describe('printRouter', () => {
  it('STAR_IO10 never calls ESC/POS native', async () => {
    const escPos = jest.fn(async () => ({success: true, message: 'esc'}));
    const starText = jest.fn(async () => ({success: true, message: 'star'}));
    const result = await routePrint(
      {action: 'printText', printer: starPrinter(), text: 'KOT'},
      {escPos, starText},
    );
    expect(escPos).not.toHaveBeenCalled();
    expect(starText).toHaveBeenCalledTimes(1);
    expect(result.success).toBe(true);
    expect(shouldSkipEscPosTransport('STAR_IO10')).toBe(true);
  });

  it('ESC_POS uses existing native path only', async () => {
    const escPos = jest.fn(async () => ({success: true, message: 'lan-9100'}));
    const starText = jest.fn(async () => ({success: true, message: 'star'}));
    const printer = emptyConfig().printer;
    const result = await routePrint(
      {action: 'printText', printer, text: 'KOT'},
      {escPos, starText},
    );
    expect(starText).not.toHaveBeenCalled();
    expect(escPos).toHaveBeenCalledTimes(1);
    expect(result.message).toBe('lan-9100');
  });

  it('routes PassPRNT and CloudPRNT without StarIO10 or ESC/POS', async () => {
    const escPos = jest.fn();
    const starText = jest.fn();
    const passPrnt = jest.fn(async () => ({success: true, message: 'pass'}));
    const cloudPrnt = jest.fn(async () => ({success: true, message: 'cloud'}));
    const pass = await routePrint(
      {action: 'printText', printer: {...starPrinter(), printEngine: 'PASSPRNT'}, text: 'A'},
      {escPos, starText, passPrnt, cloudPrnt},
    );
    const cloud = await routePrint(
      {
        action: 'printText',
        printer: {...starPrinter(), printEngine: 'CLOUDPRNT', cloudPrntUrl: 'https://cloud.example/prnt'},
        text: 'B',
      },
      {escPos, starText, passPrnt, cloudPrnt},
    );
    expect(escPos).not.toHaveBeenCalled();
    expect(starText).not.toHaveBeenCalled();
    expect(pass.success).toBe(true);
    expect(cloud.success).toBe(true);
  });

  it('records durationMs for performance checks', async () => {
    const result = await routePrint(
      {action: 'printText', printer: starPrinter(), text: 'x'},
      {starText: async () => ({success: true, message: 'ok'})},
    );
    expect(typeof result.data?.durationMs).toBe('number');
    expect((result.data?.durationMs as number) >= 0).toBe(true);
  });

  it('STAR_IO10 printImage uses the image path and skips ESC/POS', async () => {
    const escPos = jest.fn();
    const starImage = jest.fn(async () => ({success: true, message: 'img'}));
    const result = await routePrint(
      {action: 'printImage', printer: starPrinter(), imagePath: '/tmp/kot.png'},
      {escPos, starImage},
    );
    expect(escPos).not.toHaveBeenCalled();
    expect(starImage).toHaveBeenCalledWith(expect.objectContaining({printEngine: 'STAR_IO10'}), '/tmp/kot.png');
    expect(result.success).toBe(true);
  });

  it('STAR_IO10 printImage fails without a file path', async () => {
    const result = await routePrint(
      {action: 'printImage', printer: starPrinter()},
      {starImage: async () => ({success: true, message: 'should not run'})},
    );
    expect(result.success).toBe(false);
    expect(result.errorCode).toBe('INVALID_RECEIPT');
  });
});
