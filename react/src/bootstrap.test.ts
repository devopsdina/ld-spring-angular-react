import { BOOTSTRAP_TIMEOUT_MS, loadBootstrap, userPart } from './bootstrap';

const user = {
  key: 'you@example.com',
  email: 'you@example.com',
  plan: 'gold',
  framework: 'react' as const,
};

afterEach(() => {
  vi.useRealTimers();
  vi.restoreAllMocks();
});

describe('loadBootstrap', () => {
  it("asks Spring Boot for the user's context and flags, sending the user's values", async () => {
    const body = { context: { kind: 'user', key: 'you@example.com' }, flags: { $valid: true } };
    const fetch = vi.spyOn(globalThis, 'fetch').mockResolvedValue(Response.json(body));

    expect(await loadBootstrap(user)).toEqual(body);
    expect(String(fetch.mock.calls[0][0])).toBe(
      '/api/spring-boot/flags/bootstrap?email=you%40example.com&plan=gold&framework=react',
    );
  });

  it('starts without a bootstrap when the backend is not connected to LaunchDarkly (503)', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(null, { status: 503 }));

    expect(await loadBootstrap(user)).toBeUndefined();
  });

  it('gives up after 500ms, the SDK init budget, when the backend does not answer', async () => {
    vi.useFakeTimers();
    vi.spyOn(globalThis, 'fetch').mockImplementation(
      (_url, init) =>
        new Promise((_resolve, reject) =>
          init?.signal?.addEventListener('abort', () =>
            reject(new DOMException('aborted', 'AbortError')),
          ),
        ),
    );

    const result = loadBootstrap(user);
    await vi.advanceTimersByTimeAsync(BOOTSTRAP_TIMEOUT_MS);

    expect(await result).toBeUndefined();
    expect(BOOTSTRAP_TIMEOUT_MS).toBe(500);
  });
});

describe('userPart', () => {
  it('drops the kind, for the user part of a multi-context', () => {
    expect(userPart({ kind: 'user', key: 'k', plan: 'gold' })).toEqual({ key: 'k', plan: 'gold' });
  });
});
