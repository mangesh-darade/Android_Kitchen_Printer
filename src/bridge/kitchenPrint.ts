export function parsePrintPayload(raw?: string): string {
  if (!raw) {
    return '';
  }
  const trimmed = raw.trim();
  if (!trimmed) {
    return '';
  }
  try {
    const json = JSON.parse(trimmed);
    if (json && typeof json.text === 'string') {
      return json.text;
    }
    if (json && typeof json.data === 'string') {
      return json.data;
    }
  } catch {
    // raw kitchen text
  }
  return trimmed;
}

export function formatKotText(rawText?: string, targetCpl: number = 48): string {
  if (!rawText) {
    return '';
  }
  const lines = rawText.split(/\r?\n/);
  const formatted: string[] = [];

  for (const line of lines) {
    const trimmed = line.trim();
    if (!trimmed) {
      formatted.push('');
      continue;
    }

    // Dividers
    if (trimmed.length >= 3 && (/^[-=_*~]{3,}$/.test(trimmed) || /^[-=]+$/.test(trimmed))) {
      const char = trimmed.includes('=') ? '=' : '-';
      formatted.push(char.repeat(targetCpl));
      continue;
    }

    // Table header (Items Qty)
    const lower = trimmed.toLowerCase();
    if ((lower.startsWith('item') || lower.startsWith('items')) && (lower.includes('qty') || lower.includes('amount') || lower.includes('rate'))) {
      const parts = trimmed.split(/\s{2,}/);
      const left = parts[0] || trimmed;
      const right = parts.length > 1 ? parts.slice(1).join(' ') : '';
      const spaces = Math.max(1, targetCpl - left.length - right.length);
      formatted.push(left + ' '.repeat(spaces) + right);
      continue;
    }

    // Item row with quantity (e.g. • Burger (Small) 1.00)
    const match = trimmed.match(/^(.*?)\s{2,}(\d+(?:\.\d+)?(?:\s+[\d.,]+)?)\s*$/) ||
      trimmed.match(/^([•\-*]\s*.*?)\s+(\d+(?:\.\d+)?)\s*$/);
    if (match) {
      const item = match[1].trim();
      const qty = match[2].trim();
      const maxLeft = Math.max(0, targetCpl - qty.length - 1);
      const itemStr = item.length > maxLeft ? item.slice(0, maxLeft) : item;
      const spaces = Math.max(1, targetCpl - itemStr.length - qty.length);
      formatted.push(itemStr + ' '.repeat(spaces) + qty);
      continue;
    }

    // Header/Meta info (KOT No, Date, Order Type, Table)
    if (/^(kot no|token|table|order type|date|time|cashier|waiter|invoice|gst|thank)/i.test(lower) ||
        (lower.includes('/') && (lower.includes('am') || lower.includes('pm')))) {
      const pad = Math.max(0, Math.floor((targetCpl - trimmed.length) / 2));
      formatted.push(' '.repeat(pad) + trimmed);
      continue;
    }

    if (line.startsWith('   ') || trimmed.length <= Math.floor(targetCpl * 0.6)) {
      const pad = Math.max(0, Math.floor((targetCpl - trimmed.length) / 2));
      formatted.push(' '.repeat(pad) + trimmed);
      continue;
    }

    formatted.push(trimmed);
  }

  return formatted.join('\n');
}

export function kitchenPrintViaAndroidBridge(
  bridge: {printText: (json: string) => string; cutPaper?: () => string},
  text: string,
  options?: {cutIncludedInPrint?: boolean; targetCpl?: number},
): boolean {
  const content = String(text || '').trim();
  if (!content) {
    return false;
  }
  try {
    const formatted = formatKotText(content, options?.targetCpl || 48);
    const result = JSON.parse(bridge.printText(JSON.stringify({text: formatted})) || '{}');
    if (result.success === true) {
      if (!options?.cutIncludedInPrint && typeof bridge.cutPaper === 'function') {
        bridge.cutPaper();
      }
      return true;
    }
  } catch {
    return false;
  }
  return false;
}
