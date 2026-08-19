import {Linking} from 'react-native';
import type {PluginResult, PrinterConfig} from '../core/config/models';
import {passPrntDotWidth} from './enginePolicy';
import {passPrntImageHtml} from './imagePayload';

const SCHEME = 'starpassprnt';

export type PassPrntJob = {
  text: string;
  printer: PrinterConfig;
  openDrawer?: boolean;
  imageBase64?: string;
};

/** Official PassPRNT URL scheme (iOS + Android). Requires Star PassPRNT app. */
export function buildPassPrntUri(job: PassPrntJob): string {
  const size = passPrntDotWidth(job.printer.width);
  const html = job.imageBase64
    ? passPrntImageHtml(job.imageBase64)
    : `<html><body><pre style="font-size:14px;white-space:pre-wrap">${escapeHtml(
        job.text,
      )}</pre></body></html>`;
  const params: string[] = [
    `html=${encodeURIComponent(html)}`,
    `size=${size}`,
    `cut=${job.printer.autoCut ? (job.printer.cutMode === 'full' ? 'full' : 'partial') : 'partial'}`,
    `popup=enable`,
  ];
  if (job.printer.passPrntPort.trim()) {
    params.push(`port=${encodeURIComponent(job.printer.passPrntPort.trim())}`);
  }
  if (job.printer.passPrntSettings.trim()) {
    params.push(`settings=${encodeURIComponent(job.printer.passPrntSettings.trim())}`);
  }
  if (job.openDrawer || job.printer.cashDrawer) {
    params.push('drawer=after');
  }
  return `${SCHEME}://v1/print/nopreview?${params.join('&')}`;
}

export async function printWithPassPrnt(job: PassPrntJob): Promise<PluginResult> {
  const uri = buildPassPrntUri(job);
  const supported = await Linking.canOpenURL(uri);
  if (!supported) {
    return {
      success: false,
      errorCode: 'UNSUPPORTED_OPERATION',
      message: 'Star PassPRNT app is not installed.',
    };
  }
  await Linking.openURL(uri);
  return {success: true, message: 'Opened PassPRNT'};
}

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');
}
