import {emptyConfig} from '../core/config/models';
import {buildPassPrntUri} from './passPrnt';

describe('PassPRNT URL scheme', () => {
  it('builds official nopreview URI with size and optional drawer', () => {
    const printer = {
      ...emptyConfig().printer,
      width: '3inch' as const,
      autoCut: true,
      cutMode: 'partial' as const,
      cashDrawer: true,
      passPrntPort: 'BT:00:11:62:00:00:01',
    };
    const uri = buildPassPrntUri({text: 'KOT #1', printer});
    expect(uri.startsWith('starpassprnt://v1/print/nopreview?')).toBe(true);
    expect(uri).toContain('size=576');
    expect(uri).toContain('drawer=after');
    expect(uri).toContain('cut=partial');
    expect(uri).toContain(encodeURIComponent('BT:00:11:62:00:00:01'));
  });

  it('uses 832 dots for 4 inch paper', () => {
    const printer = {...emptyConfig().printer, width: '4inch' as const};
    expect(buildPassPrntUri({text: 'x', printer})).toContain('size=832');
  });

  it('embeds a base64 image in PassPRNT HTML', () => {
    const printer = emptyConfig().printer;
    const uri = buildPassPrntUri({text: '', printer, imageBase64: 'Zm9v'});
    expect(decodeURIComponent(uri)).toContain('data:image/png;base64,Zm9v');
  });
});
