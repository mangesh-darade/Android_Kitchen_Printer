import {passPrntImageHtml, stripImageBase64} from './imagePayload';

describe('imagePayload', () => {
  it('strips data-URL prefix and whitespace', () => {
    expect(stripImageBase64('data:image/png;base64,abc\n123')).toBe('abc123');
    expect(stripImageBase64('  xyz  ')).toBe('xyz');
  });

  it('builds PassPRNT HTML for a raster image', () => {
    const html = passPrntImageHtml('data:image/png;base64,Zm9v');
    expect(html).toContain('data:image/png;base64,Zm9v');
    expect(html).toContain('<img');
  });
});
