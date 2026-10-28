// Runs App with the real React SDK against a local LaunchDarkly stand-in. Only the product API is
// mocked.
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import type { LDReactClient } from '@launchdarkly/react-sdk';

import type { ProductOffer } from './product';
import { type FakeLaunchDarkly, startFakeLaunchDarkly } from './test/fake-launchdarkly-server';
import { requestedContexts } from './test/contexts';
import { Probe } from './test/probe';

const camera: ProductOffer = {
  id: 2,
  name: 'Camera',
  url: 'camera.jpg',
  type: 'electronics',
  originalPrice: 600,
  price: 300,
  discount: 0.5,
  discountReason: 'RULE_MATCH(0,rule-id)',
};

vi.mock('./product', async (importOriginal) => ({
  ...(await importOriginal<typeof import('./product')>()),
  getProduct: vi.fn(async () => camera),
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
  vi.resetModules();
});

afterEach(async () => {
  cleanup();
  await client?.close();
  client = undefined;
  fake.dropConnections();
});

async function renderApp() {
  const { default: App } = await import('./App');
  const { createLaunchDarklyProvider } = await import('./launchdarkly/launchdarkly');
  const { userContext } = await import('./user');
  const Provider = createLaunchDarklyProvider('test-client-side-id', userContext, fake.options);
  render(
    <Provider>
      <Probe flag="enable-purchasing" onClient={(c) => (client = c)} />
      <App />
    </Provider>,
  );
  await waitFor(() => expect(screen.getByText('connected')).toBeInTheDocument());
}

describe('App with the real SDK', () => {
  it('shows the discounted product and the server-side reason', async () => {
    await renderApp();

    await waitFor(() => expect(screen.getByText('$300.00')).toBeInTheDocument());
    expect(screen.getByText('was $600.00')).toBeInTheDocument();
    expect(screen.getByText(/RULE_MATCH\(0,rule-id\)/)).toBeInTheDocument();
  });

  it('keeps ordering off while enable-purchasing is false, and shows why', async () => {
    await renderApp();

    expect(screen.getByRole('button', { name: 'Place order' })).toBeDisabled();
    expect(screen.getByText(/false · FALLTHROUGH/)).toBeInTheDocument();
  });

  it('enables ordering when the flag serves true for the user and product', async () => {
    // Served only to the multi-context the App identifies with once the product loads.
    fake.flags['product:2'] = { 'enable-purchasing': true };
    await renderApp();

    await waitFor(() => expect(screen.getByRole('button', { name: 'Place order' })).toBeEnabled());
  });

  it('identifies the user and product together once the product loads', async () => {
    await renderApp();

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
