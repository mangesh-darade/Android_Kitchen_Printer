import type {PluginResult, PrinterConfig} from '../core/config/models';
import {stripImageBase64} from './imagePayload';

const TEXT_PLAIN = 'text/plain';
const IMAGE_PNG = 'image/png';

export type CloudPrntJob = {
  text: string;
  printer: PrinterConfig;
  imageBase64?: string;
};

/**
 * Push a kitchen/receipt job to a CloudPRNT endpoint.
 * Printers poll that server (official CloudPRNT). POS Connect posts text/plain
 * so a CloudPRNT server (or printer HTTP listener) can pick it up.
 * Image jobs POST image/png.
 */
export function cloudPrntHeaders(contentType: string = TEXT_PLAIN): Record<string, string> {
  return {
    'Content-Type': contentType,
    Accept: 'application/json, text/plain, */*',
  };
}

export async function printWithCloudPrnt(job: CloudPrntJob): Promise<PluginResult> {
  const url = job.printer.cloudPrntUrl.trim();
  if (!url) {
    return {
      success: false,
      errorCode: 'INVALID_RECEIPT',
      message: 'CloudPRNT URL is required.',
    };
  }
  try {
    const image = job.imageBase64?.trim();
    const contentType = image ? IMAGE_PNG : TEXT_PLAIN;
    const target = url.includes('?')
      ? `${url}&type=${encodeURIComponent(contentType)}`
      : url;
    const body = image ? base64ToBytes(image) : job.text;
    const response = await fetch(target, {
      method: 'POST',
      headers: cloudPrntHeaders(contentType),
      body,
    });
    if (!response.ok) {
      return {
        success: false,
        errorCode: 'NETWORK_ERROR',
        message: `CloudPRNT HTTP ${response.status}`,
      };
    }
    return {success: true, message: image ? 'CloudPRNT image accepted' : 'CloudPRNT job accepted'};
  } catch (error) {
    return {
      success: false,
      errorCode: 'NETWORK_ERROR',
      message: error instanceof Error ? error.message : 'CloudPRNT request failed',
    };
  }
}

function base64ToBytes(raw: string): Uint8Array {
  const clean = stripImageBase64(raw);
  if (typeof globalThis.atob === 'function') {
    const binary = globalThis.atob(clean);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i += 1) {
      bytes[i] = binary.charCodeAt(i);
    }
    return bytes;
  }
  return Uint8Array.from(Buffer.from(clean, 'base64'));
}
