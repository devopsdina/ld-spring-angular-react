import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { App, PURCHASE_FLAG } from './app';
import { closeLaunchDarkly } from './launchdarkly/ld-client';
import {
  MockLaunchDarklyClient,
  useMockLaunchDarkly,
} from './launchdarkly/testing/mock-launchdarkly-client';
import { ProductOffer } from './product.service';

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

describe('App', () => {
  let client: MockLaunchDarklyClient;

  beforeEach(() => {
    client = new MockLaunchDarklyClient({ [PURCHASE_FLAG]: false });
    useMockLaunchDarkly(client);
    TestBed.configureTestingModule({
      imports: [App],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
  });

  afterEach(() => closeLaunchDarkly());

  async function render() {
    const fixture = TestBed.createComponent(App);
    TestBed.inject(HttpTestingController)
      .expectOne((req) => req.url.startsWith('/api/product/'))
      .flush(camera);
    await fixture.whenStable();
    return fixture;
  }

  it('shows the discounted product and the server-side reason', async () => {
    const el: HTMLElement = (await render()).nativeElement;

    expect(el.querySelector('.productPrice')?.textContent).toContain('$300.00');
    expect(el.querySelector('.productOriginalPrice')?.textContent).toContain('$600.00');
    expect(el.textContent).toContain('RULE_MATCH(0,rule-id)');
  });

  it('enables ordering when enable-purchasing turns on, and shows why', async () => {
    const fixture = await render();
    const el: HTMLElement = fixture.nativeElement;
    const button = el.querySelector<HTMLButtonElement>('.orderButton')!;
    expect(button.disabled).toBe(true);

    client.setFlags({ [PURCHASE_FLAG]: true });
    await fixture.whenStable();

    expect(button.disabled).toBe(false);
    expect(el.querySelector('.flagInfo')?.textContent).toContain('true · FALLTHROUGH');
  });

  it('identifies the user and product together once the product loads', async () => {
    const identify = vi.spyOn(client, 'identify');

    await render();

    expect(identify).toHaveBeenCalledWith(
      expect.objectContaining({
        kind: 'multi',
        user: expect.objectContaining({ framework: 'angular' }),
        product: expect.objectContaining({ key: '2', type: 'electronics', price: 600 }),
      }),
      { timeout: 0.5 },
    );
  });
});
