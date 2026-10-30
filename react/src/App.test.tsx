// Runs App with the real React SDK against a local LaunchDarkly stand-in. Only the product API is
// mocked.
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import type { LDReactClient } from '@launchdarkly/react-sdk';

import type { BackendChoice } from './backend';
import type { ProductOffer } from './product';
import { type FakeLaunchDarkly, startFakeLaunchDarkly } from './test/fake-launchdarkly-server';
import { requestedContexts } from './test/contexts';
import { Probe } from './test/probe';

const VERSIONS = { 'spring-boot': '4.1.2', quarkus: '3.40.1.1' } as const;
const NAMES = { 'spring-boot': 'Spring Boot', quarkus: 'Quarkus' } as const;

function camera(backend: BackendChoice): ProductOffer {
  return {
    id: 2,
    name: 'Camera',
    url: 'camera.jpg',
    type: 'electronics',
    originalPrice: 600,
    price: 300,
    discount: 0.5,
    discountReason: 'RULE_MATCH(0,rule-id)',
    backend: { id: backend.id, name: NAMES[backend.id], version: VERSIONS[backend.id] },
  };
}

const getProduct = vi.hoisted(() => vi.fn());
vi.mock('./product', async (importOriginal) => ({
  ...(await importOriginal<typeof import('./product')>()),
  getProduct,
}));

let fake: FakeLaunchDarkly;
let client: LDReactClient | undefined;

beforeAll(async () => {
  fake = await startFakeLaunchDarkly();
});

afterAll(() => fake.close());

beforeEach(() => {
  fake.mode = 'respond';
  fake.flags = { default: { 'enable-purchasing': false } };
  fake.requests = [];
  fake.tags = [];
  localStorage.clear();
  getProduct.mockReset();
  getProduct.mockImplementation(async (_id: number, _user: unknown, backend: BackendChoice) =>
    camera(backend),
  );
  window.history.replaceState(null, '', '/');
  vi.resetModules();
});

afterEach(async () => {
  cleanup();
  await client?.close();
  client = undefined;
  fake.dropConnections();
  delete document.body.dataset.backend;
});

async function renderApp(bootstrap?: unknown) {
  const { default: App } = await import('./App');
  const { createLaunchDarklyProvider } = await import('./launchdarkly/launchdarkly');
  const { userContext } = await import('./user');
  const Provider = createLaunchDarklyProvider(
    'test-client-side-id',
    userContext,
    fake.options,
    bootstrap,
  );
  render(
    <Provider>
      <Probe flag="enable-purchasing" onClient={(c) => (client = c)} />
      <App />
    </Provider>,
  );
}

async function renderConnected() {
  await renderApp();
  await waitFor(() => expect(screen.getByText('connected')).toBeInTheDocument());
}

/** The backends getProduct was asked for, in order. */
function backendsRequested(): string[] {
  return getProduct.mock.calls.map((call) => (call[2] as BackendChoice).id);
}

describe('App with the real SDK', () => {
  it('shows the discounted product and the server-side reason', async () => {
    await renderConnected();

    await waitFor(() => expect(screen.getByText('$300.00')).toBeInTheDocument());
    expect(screen.getByText('was $600.00')).toBeInTheDocument();
    expect(screen.getByText(/RULE_MATCH\(0,rule-id\)/)).toBeInTheDocument();
  });

  it('keeps ordering off while enable-purchasing is false, and shows why', async () => {
    await renderConnected();

    expect(screen.getByRole('button', { name: 'Place order' })).toBeDisabled();
    expect(screen.getByText(/false · FALLTHROUGH/)).toBeInTheDocument();
  });

  it('enables ordering when the flag serves true for the user and product', async () => {
    // Served only to the multi-context the App identifies with once the product loads.
    fake.flags['product:2'] = { 'enable-purchasing': true };
    await renderConnected();

    await waitFor(() => expect(screen.getByRole('button', { name: 'Place order' })).toBeEnabled());
  });

  it('identifies the user and product together once the product loads', async () => {
    await renderConnected();

    await waitFor(() =>
      expect(requestedContexts(fake.requests)).toContainEqual(
        expect.objectContaining({
          kind: 'multi',
          user: expect.objectContaining({ framework: 'react' }),
          product: expect.objectContaining({ key: '2', type: 'electronics', price: 600 }),
        }),
      ),
    );
  });
});

describe('Backend routing with api-backend', () => {
  it('calls Spring Boot when the flag is missing, and says so', async () => {
    await renderConnected();

    await waitFor(() =>
      expect(screen.getByText('Served by Spring Boot 4.1.2')).toBeInTheDocument(),
    );
    expect(backendsRequested()).toEqual(['spring-boot']);
    expect((getProduct.mock.calls[0][2] as BackendChoice).routing).toBe('flag:ERROR');
    await waitFor(() =>
      expect(screen.getByText('spring-boot · ERROR (FLAG_NOT_FOUND)')).toBeInTheDocument(),
    );
    expect(document.body.dataset.backend).toBe('spring-boot');
  });

  it('waits for flags, then calls only the backend the flag picks', async () => {
    fake.flags.default['api-backend'] = 'quarkus';
    await renderConnected();

    await waitFor(() => expect(screen.getByText('Served by Quarkus 3.40.1.1')).toBeInTheDocument());
    expect(backendsRequested()).toEqual(['quarkus']);
    expect(screen.getByText('quarkus · FALLTHROUGH')).toBeInTheDocument();
    expect(screen.getByText('⚡ Quarkus 3.40.1.1')).toBeInTheDocument();
    expect(document.body.dataset.backend).toBe('quarkus');
  });

  it('uses bootstrapped flags on the first render, before LaunchDarkly answers', async () => {
    fake.mode = 'hang';
    await renderApp({
      'api-backend': 'quarkus',
      'enable-purchasing': true,
      $flagsState: {
        'api-backend': {
          variation: 1,
          version: 1,
          reason: { kind: 'RULE_MATCH', ruleIndex: 0, ruleId: 'gold' },
        },
        'enable-purchasing': { variation: 0, version: 1, reason: { kind: 'FALLTHROUGH' } },
      },
      $valid: true,
    });
    // The first render already has the bootstrapped values.
    expect(screen.getByRole('button', { name: 'Place order' })).toBeEnabled();
    expect(screen.getByText('quarkus · RULE_MATCH')).toBeInTheDocument();

    await waitFor(() => expect(screen.getByText('Served by Quarkus 3.40.1.1')).toBeInTheDocument());
    expect((getProduct.mock.calls[0][2] as BackendChoice).routing).toBe('flag:RULE_MATCH');
    // identify() times out against the hanging LaunchDarkly; routing stays on Quarkus.
    await new Promise((resolve) => setTimeout(resolve, 800));
    expect(backendsRequested()).toEqual(['quarkus']);
  });

  it('switches backend live when the flag changes, and refetches the same product', async () => {
    // Quarkus only once the App identifies with the product, like a targeting change mid-session.
    fake.flags['product:2'] = { 'api-backend': 'quarkus' };
    await renderConnected();

    await waitFor(() => expect(screen.getByText('Served by Quarkus 3.40.1.1')).toBeInTheDocument());
    expect(backendsRequested()).toEqual(['spring-boot', 'quarkus']);
    const productIds = getProduct.mock.calls.map((call) => call[0]);
    expect(new Set(productIds).size).toBe(1);
  });

  it('aborts the request to the backend it switched away from', async () => {
    fake.flags['product:2'] = { 'api-backend': 'quarkus' };
    // Spring Boot answers the first request, then never again, so the second one is still open
    // when the flag switches to Quarkus.
    getProduct.mockImplementation(
      async (_id: number, _user: unknown, backend: BackendChoice, signal: AbortSignal) => {
        if (backend.id === 'spring-boot' && getProduct.mock.calls.length > 1) {
          await new Promise((resolve) => signal.addEventListener('abort', resolve));
        }
        return camera(backend);
      },
    );
    await renderConnected();

    await waitFor(() => expect(screen.getByText('Served by Quarkus 3.40.1.1')).toBeInTheDocument());
    const signals = getProduct.mock.calls.map((call) => call[3] as AbortSignal);
    expect(signals.slice(0, -1).every((signal) => signal.aborted)).toBe(true);
    expect(signals.at(-1)?.aborted).toBe(false);
  });

  it('lets ?backend= override the flag, and labels it', async () => {
    window.history.replaceState(null, '', '/?backend=quarkus');
    await renderConnected();

    await waitFor(() => expect(screen.getByText('Served by Quarkus 3.40.1.1')).toBeInTheDocument());
    expect((getProduct.mock.calls[0][2] as BackendChoice).routing).toBe('url-override');
    expect(screen.getByText('quarkus · overridden by URL')).toBeInTheDocument();
  });

  it('shows the backend as unavailable when it does not answer', async () => {
    fake.flags.default['api-backend'] = 'quarkus';
    getProduct.mockRejectedValue(new DOMException('timed out', 'AbortError'));
    await renderConnected();

    await waitFor(() => expect(screen.getByText('Quarkus unavailable')).toBeInTheDocument());
    expect(screen.getByText('⚡ Quarkus · unavailable')).toBeInTheDocument();
    expect(screen.getByText(/Could not load a product from Quarkus/)).toBeInTheDocument();
  });
});
