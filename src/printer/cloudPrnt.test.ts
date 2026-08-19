import {emptyConfig} from '../core/config/models';
import {printWithCloudPrnt} from './cloudPrnt';

describe('CloudPRNT', () => {
  it('fails fast without URL (no network call)', async () => {
    const result = await printWithCloudPrnt({
      text: 'KOT',
      printer: emptyConfig().printer,
    });
    expect(result.success).toBe(false);
    expect(result.errorCode).toBe('INVALID_RECEIPT');
  });

  it('posts image/png when a raster job is supplied', async () => {
    const fetchMock = jest.fn(async () => ({ok: true})) as unknown as typeof fetch;
    global.fetch = fetchMock;
    const result = await printWithCloudPrnt({
      text: '',
      imageBase64: 'Zm9v',
      printer: {...emptyConfig().printer, cloudPrntUrl: 'https://cloud.example/prnt'},
    });
    expect(result.success).toBe(true);
    expect(fetchMock).toHaveBeenCalledWith(
      'https://cloud.example/prnt',
      expect.objectContaining({
        method: 'POST',
        headers: expect.objectContaining({'Content-Type': 'image/png'}),
      }),
    );
  });
});
