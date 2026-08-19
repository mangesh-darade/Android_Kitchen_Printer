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

export function kitchenPrintViaAndroidBridge(
  bridge: {printText: (json: string) => string; cutPaper?: () => string},
  text: string,
): boolean {
  const content = String(text || '').trim();
  if (!content) {
    return false;
  }
  try {
    const result = JSON.parse(bridge.printText(JSON.stringify({text: content})) || '{}');
    if (result.success === true) {
      if (typeof bridge.cutPaper === 'function') {
        bridge.cutPaper();
      }
      return true;
    }
  } catch {
    return false;
  }
  return false;
}
