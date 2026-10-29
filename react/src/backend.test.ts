import contract from '../../contracts/contexts.json';
import { DEFAULT_BACKEND, chooseBackend, urlOverride } from './backend';
import { getProduct } from './product';
import { userContext } from './user';

describe('chooseBackend', () => {
  it('follows the flag and passes its reason on as the routing', () => {
    const choice = chooseBackend(
      { value: 'quarkus', reason: { kind: 'RULE_MATCH', ruleIndex: 0, ruleId: 'gold' } },
      null,
    );

    expect(choice).toEqual({
      id: 'quarkus',
      routing: 'flag:RULE_MATCH',
      description: 'quarkus · RULE_MATCH',
    });
  });

  it('falls back to Spring Boot for a value it does not recognize, so a flag can never pick a URL', () => {
    expect(
      chooseBackend({ value: 'https://evil.example', reason: { kind: 'FALLTHROUGH' } }, null).id,
    ).toBe(DEFAULT_BACKEND);
  });

  it('lets the URL override win over the flag', () => {
    expect(
      chooseBackend({ value: 'spring-boot', reason: { kind: 'FALLTHROUGH' } }, 'quarkus'),
    ).toEqual({
      id: 'quarkus',
      routing: 'url-override',
      description: 'quarkus · overridden by URL',
    });
  });
});

describe('urlOverride', () => {
  it('reads ?backend= and ignores anything but a known backend', () => {
    expect(urlOverride('?backend=quarkus')).toBe('quarkus');
    expect(urlOverride('?backend=spring-boot')).toBe('spring-boot');
    expect(urlOverride('?backend=other')).toBeNull();
    expect(urlOverride('')).toBeNull();
  });
});

describe('getProduct', () => {
  afterEach(() => vi.restoreAllMocks());

  it('calls the chosen backend through its proxy path, with the routing header', async () => {
    const fetch = vi.spyOn(globalThis, 'fetch').mockResolvedValue(Response.json({ id: 2 }));
    const user = { key: 'k', email: 'e', plan: 'gold', framework: 'react' as const };

    await getProduct(
      2,
      user,
      { id: 'quarkus', routing: 'flag:RULE_MATCH', description: '' },
      new AbortController().signal,
    );

    const [url, init] = fetch.mock.calls[0];
    expect(String(url)).toMatch(/^\/api\/quarkus\/product\/2\?/);
    expect(init?.headers).toEqual({ 'X-Backend-Routing': 'flag:RULE_MATCH' });
  });

  it('gives up after 2s when the backend does not answer', async () => {
    vi.useFakeTimers();
    vi.spyOn(globalThis, 'fetch').mockImplementation(
      (_url, init) =>
        new Promise((_resolve, reject) =>
          init?.signal?.addEventListener('abort', () =>
            reject(new DOMException('aborted', 'AbortError')),
          ),
        ),
    );
    const user = { key: 'k', email: 'e', plan: 'gold', framework: 'react' as const };
    const request = getProduct(
      2,
      user,
      { id: 'quarkus', routing: 'flag:FALLTHROUGH', description: '' },
      new AbortController().signal,
    );
    const settled = expect(request).rejects.toThrow('aborted');

    await vi.advanceTimersByTimeAsync(1999);
    await vi.advanceTimersByTimeAsync(1);
    await settled;
    vi.useRealTimers();
  });
});

describe('contexts', () => {
  it('builds the same user context as the backends (contracts/contexts.json)', () => {
    expect(userContext).toEqual(contract.users.react);
  });
});
