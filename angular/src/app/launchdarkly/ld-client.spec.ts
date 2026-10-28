// Runs the real SDK against a local server standing in for LaunchDarkly, so the preflight rules
// are measured, not mocked.
import {
  IDENTIFY_TIMEOUT_SECONDS,
  INIT_TIMEOUT_SECONDS,
  closeLaunchDarkly,
  getLaunchDarkly,
  identifyContext,
  initLaunchDarkly,
  onContextChange,
} from './ld-client';
import {
  FakeLaunchDarkly,
  expectAboutTimeout,
  startFakeLaunchDarkly,
  timed,
} from './testing/fake-launchdarkly-server.node';

let fake: FakeLaunchDarkly;

beforeAll(async () => {
  fake = await startFakeLaunchDarkly();
});

afterAll(() => fake.close());

beforeEach(() => {
  fake.mode = 'respond';
  fake.flags = { default: { 'demo-flag': true } };
  fake.requests = [];
  fake.tags = [];
  // The SDK caches flags in localStorage and serves them when LaunchDarkly is unreachable.
  // Clear it so each test starts as a first-time visitor.
  localStorage.clear();
});

afterEach(async () => {
  await closeLaunchDarkly();
  fake.dropConnections();
});

function init(logger = { warn: vi.fn() }) {
  return initLaunchDarkly({
    clientSideID: 'test-client-side-id',
    context: { kind: 'user', key: 'user-1' },
    options: fake.options,
    logger,
  });
}

describe('ld-client with the real SDK', () => {
  describe('singleton initialization', () => {
    it('creates one client and reuses it on a second call, with a warning', async () => {
      const first = init();
      await first.ready;

      const logger = { warn: vi.fn() };
      const second = init(logger);

      expect(second).toBe(first);
      expect(getLaunchDarkly()).toBe(first.client);
      expect(logger.warn).toHaveBeenCalledOnce();
      expect(logger.warn.mock.calls[0][0]).toContain('not re-initialized');
    });
  });

  describe('evaluation reasons', () => {
    it('asks LaunchDarkly for reasons and reports them in variationDetail()', async () => {
      const { client, ready } = init();
      await ready;

      expect(fake.requests.some((url) => url.includes('withReasons=true'))).toBe(true);
      expect(client.variationDetail('demo-flag', false).reason).toEqual({ kind: 'FALLTHROUGH' });
    });
  });

  describe('application metadata', () => {
    it('sends applicationInfo with SDK requests', async () => {
      const { ready } = initLaunchDarkly({
        clientSideID: 'test-client-side-id',
        context: { kind: 'user', key: 'user-1' },
        options: { ...fake.options, applicationInfo: { id: 'test-app', version: '1.2.3' } },
      });
      await ready;

      expect(fake.tags).toContain('application-id/test-app application-version/1.2.3');
    });
  });

  describe('initialization timeout', () => {
    it('is 500ms', () => {
      expect(INIT_TIMEOUT_SECONDS).toBe(0.5);
    });

    it('completes and serves flags when LaunchDarkly answers', async () => {
      const { client, ready } = init();

      expect((await ready).status).toBe('complete');
      expect(client.boolVariation('demo-flag', false)).toBe(true);
    });

    it('stops waiting at 500ms when LaunchDarkly never answers, and serves fallbacks', async () => {
      fake.mode = 'hang';
      const { client, ready } = init();

      const { value, ms } = await timed(ready);

      expect(value.status).toBe('timeout');
      expectAboutTimeout(ms);
      expect(client.boolVariation('demo-flag', false)).toBe(false);
    });
  });

  describe('identify timeout', () => {
    it('is 500ms', () => {
      expect(IDENTIFY_TIMEOUT_SECONDS).toBe(0.5);
    });

    it('completes and loads the new context flags when LaunchDarkly answers', async () => {
      fake.flags['user-2'] = { 'demo-flag': false };
      const { client, ready } = init();
      await ready;

      const result = await identifyContext({ kind: 'user', key: 'user-2' });

      expect(result.status).toBe('completed');
      expect(client.boolVariation('demo-flag', true)).toBe(false);
    });

    it('stops waiting at 500ms when LaunchDarkly never answers, and keeps current flags', async () => {
      const { client, ready } = init();
      await ready;
      fake.mode = 'hang';

      const { value, ms } = await timed(identifyContext({ kind: 'user', key: 'user-2' }));

      expect(value.status).toBe('timeout');
      expectAboutTimeout(ms);
      expect(client.boolVariation('demo-flag', false)).toBe(true);
    });

    it('throws if called before initLaunchDarkly()', async () => {
      await expect(identifyContext({ kind: 'user', key: 'user-2' })).rejects.toThrow(
        /must be called before/,
      );
    });

    it('notifies onContextChange() listeners when identify completes, not when it times out', async () => {
      const { ready } = init();
      await ready;
      const listener = vi.fn();
      const stop = onContextChange(listener);

      await identifyContext({ kind: 'user', key: 'user-2' });
      expect(listener).toHaveBeenCalledOnce();

      fake.mode = 'hang';
      await identifyContext({ kind: 'user', key: 'user-3' });
      expect(listener).toHaveBeenCalledOnce();

      stop();
    });
  });
});
