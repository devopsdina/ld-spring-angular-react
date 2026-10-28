// Runs the real SDK through the thin Angular layer: provideLaunchDarklyClient(), flagDetailSignal()
// and flagSignal().
import { ApplicationInitStatus } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import {
  flagDetailSignal,
  flagSignal,
  launchDarklyStatus,
  provideLaunchDarklyClient,
} from './flag-signal';
import { closeLaunchDarkly, getLaunchDarkly, identifyContext } from './ld-client';
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
  localStorage.clear();
  vi.spyOn(console, 'warn').mockImplementation(() => {});
});

afterEach(async () => {
  TestBed.resetTestingModule();
  await closeLaunchDarkly();
  fake.dropConnections();
  vi.restoreAllMocks();
});

// Configures the app providers and waits for app startup, like bootstrapApplication() does.
async function startApp(): Promise<void> {
  TestBed.configureTestingModule({
    providers: [
      provideLaunchDarklyClient({
        clientSideID: 'test-client-side-id',
        context: { kind: 'user', key: 'user-1' },
        options: fake.options,
      }),
    ],
  });
  await TestBed.inject(ApplicationInitStatus).donePromise;
}

describe('provideLaunchDarklyClient', () => {
  it('starts the client during app startup', async () => {
    await startApp();

    expect(getLaunchDarkly()).not.toBeNull();
    expect(launchDarklyStatus()).toBe('complete');
  });

  it('holds app startup for at most 500ms when LaunchDarkly never answers', async () => {
    fake.mode = 'hang';

    const { ms } = await timed(startApp());

    expectAboutTimeout(ms);
    expect(launchDarklyStatus()).toBe('timeout');
  });
});

describe('flagDetailSignal', () => {
  it('returns the served value and the reason', async () => {
    await startApp();

    const detail = TestBed.runInInjectionContext(() => flagDetailSignal('demo-flag', false));

    expect(detail().value).toBe(true);
    expect(detail().reason).toEqual({ kind: 'FALLTHROUGH' });
  });

  it('returns the fallback with an error reason after a startup timeout', async () => {
    fake.mode = 'hang';
    await startApp();

    const detail = TestBed.runInInjectionContext(() => flagDetailSignal('demo-flag', false));

    expect(detail().value).toBe(false);
    expect(detail().reason?.kind).toBe('ERROR');
  });

  it('re-reads after identifyContext() even when the value stays the same', async () => {
    fake.flags['user-2'] = { 'demo-flag': true };
    await startApp();
    const detail = TestBed.runInInjectionContext(() => flagDetailSignal('demo-flag', false));
    const before = detail();

    await identifyContext({ kind: 'user', key: 'user-2' });

    expect(detail().value).toBe(true);
    expect(detail()).not.toBe(before);
  });
});

describe('flagSignal', () => {
  it('returns the served value', async () => {
    await startApp();

    const value = TestBed.runInInjectionContext(() => flagSignal('demo-flag', false));

    expect(value()).toBe(true);
  });

  it('returns the fallback after a startup timeout', async () => {
    fake.mode = 'hang';
    await startApp();

    const value = TestBed.runInInjectionContext(() => flagSignal('demo-flag', false));

    expect(value()).toBe(false);
  });

  it('updates when the flag changes, for example after identifyContext()', async () => {
    fake.flags['user-2'] = { 'demo-flag': false };
    await startApp();
    const value = TestBed.runInInjectionContext(() => flagSignal('demo-flag', true));
    expect(value()).toBe(true);

    await identifyContext({ kind: 'user', key: 'user-2' });

    expect(value()).toBe(false);
  });

  it('stops listening when its component or injector is destroyed', async () => {
    await startApp();
    const client = getLaunchDarkly()!;
    const off = vi.spyOn(client, 'off');
    TestBed.runInInjectionContext(() => flagSignal('demo-flag', false));

    TestBed.resetTestingModule();

    expect(off).toHaveBeenCalledWith('change:demo-flag', expect.any(Function));
  });
});
