// Runs the real React SDK through createLaunchDarklyProvider() against a local server standing in
// for LaunchDarkly, so the preflight rules are measured, not mocked.
import type { LDReactClient } from '@launchdarkly/react-sdk';
import { cleanup, render, screen, waitFor } from '@testing-library/react';

import {
  type FakeLaunchDarkly,
  expectAboutTimeout,
  startFakeLaunchDarkly,
} from '../test/fake-launchdarkly-server';
import { Probe } from '../test/probe';

let fake: FakeLaunchDarkly;
let client: LDReactClient | undefined;

beforeAll(async () => {
  fake = await startFakeLaunchDarkly();
});

afterAll(() => fake.close());

beforeEach(() => {
  fake.mode = 'respond';
  fake.flags = { default: { 'demo-flag': true } };
  fake.requests = [];
  fake.tags = [];
  localStorage.clear();
  // The provider is a module-level singleton: load a fresh copy for each test.
  vi.resetModules();
});

afterEach(async () => {
  cleanup();
  await client?.close();
  client = undefined;
  fake.dropConnections();
  vi.restoreAllMocks();
});

async function renderProvider() {
  const { createLaunchDarklyProvider } = await import('./launchdarkly');
  const Provider = createLaunchDarklyProvider(
    'test-client-side-id',
    { kind: 'user', key: 'user-1' },
    fake.options,
  );
  const start = performance.now();
  render(
    <Provider>
      <Probe flag="demo-flag" onClient={(c) => (client = c)} />
    </Provider>,
  );
  return { start, createLaunchDarklyProvider };
}

describe('createLaunchDarklyProvider with the real SDK', () => {
  it('creates one client and reuses it on a second call, with a warning', async () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
    const { createLaunchDarklyProvider } = await import('./launchdarkly');

    const first = createLaunchDarklyProvider(
      'test-client-side-id',
      { kind: 'user', key: 'user-1' },
      fake.options,
    );
    const second = createLaunchDarklyProvider(
      'test-client-side-id',
      { kind: 'user', key: 'user-1' },
      fake.options,
    );

    expect(second).toBe(first);
    expect(warn).toHaveBeenCalledOnce();
    expect(warn.mock.calls[0][0]).toContain('not re-initialized');
    const Provider = first;
    render(
      <Provider>
        <Probe flag="demo-flag" onClient={(c) => (client = c)} />
      </Provider>,
    );
  });

  it('renders right away, then serves flags with evaluation reasons', async () => {
    await renderProvider();
    expect(screen.getByTestId('status')).toHaveTextContent('initializing');

    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('complete'));

    expect(screen.getByTestId('value')).toHaveTextContent('true');
    expect(screen.getByTestId('reason')).toHaveTextContent('FALLTHROUGH');
    expect(fake.requests.some((url) => url.includes('withReasons=true'))).toBe(true);
  });

  it('stops waiting at 500ms when LaunchDarkly never answers, and serves the fallback', async () => {
    fake.mode = 'hang';
    const { start } = await renderProvider();

    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('timeout'), {
      timeout: 2000,
    });

    expectAboutTimeout(performance.now() - start);
    expect(screen.getByTestId('value')).toHaveTextContent('false');
    expect(screen.getByTestId('reason')).toHaveTextContent('ERROR');
  });

  it('stops waiting at 500ms on identify() when LaunchDarkly never answers', async () => {
    const { IDENTIFY_TIMEOUT_SECONDS } = await import('./launchdarkly');
    await renderProvider();
    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('complete'));
    fake.mode = 'hang';

    const start = performance.now();
    const result = await client!.identify(
      { kind: 'user', key: 'user-2' },
      { timeout: IDENTIFY_TIMEOUT_SECONDS },
    );

    expect(result.status).toBe('timeout');
    expectAboutTimeout(performance.now() - start);
    expect(screen.getByTestId('value')).toHaveTextContent('true');
  });

  it('sends applicationInfo with SDK requests', async () => {
    const { createLaunchDarklyProvider } = await import('./launchdarkly');
    const Provider = createLaunchDarklyProvider(
      'test-client-side-id',
      { kind: 'user', key: 'user-1' },
      { ...fake.options, applicationInfo: { id: 'test-app', version: '1.2.3' } },
    );
    render(
      <Provider>
        <Probe flag="demo-flag" onClient={(c) => (client = c)} />
      </Provider>,
    );

    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('complete'));
    expect(fake.tags).toContain('application-id/test-app application-version/1.2.3');
  });
});
