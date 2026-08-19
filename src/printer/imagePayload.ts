export function stripImageBase64(raw: string): string {
  const trimmed = raw.trim();
  const comma = trimmed.indexOf(',');
  if (trimmed.startsWith('data:') && comma >= 0) {
    return trimmed.slice(comma + 1).replace(/\s/g, '');
  }
  return trimmed.replace(/\s/g, '');
}

export function passPrntImageHtml(base64: string): string {
  const data = stripImageBase64(base64);
  return `<html><body><img src="data:image/png;base64,${data}" style="width:100%;height:auto"/></body></html>`;
}
