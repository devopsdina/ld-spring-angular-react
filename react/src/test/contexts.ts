/** Decodes the contexts the SDK asked for flags for, from /sdk/evalx/<id>/contexts/<base64url> URLs. */
export function requestedContexts(urls: string[]): Record<string, unknown>[] {
  return urls.flatMap((url) => {
    const match = url.match(/\/contexts\/([^/?]+)/);
    return match ? [JSON.parse(Buffer.from(match[1], 'base64url').toString('utf8'))] : [];
  });
}
