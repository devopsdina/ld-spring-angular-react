import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  TestRequest,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { App, PURCHASE_FLAG } from './app';
import { BACKEND_FLAG, BackendId } from './backend';
import { closeLaunchDarkly } from './launchdarkly/ld-client';
import {
  MockLaunchDarklyClient,
  useMockLaunchDarkly,
} from './launchdarkly/testing/mock-launchdarkly-client';
import { ProductOffer } from './product.service';

const BACKEND_INFO = {
  'spring-boot': { id: 'spring-boot', name: 'Spring Boot', version: '4.1.2' },
  quarkus: { id: 'quarkus', name: 'Quarkus', version: '3.40.1.1' },
} as const;

function camera(backend: BackendId): ProductOffer {
  return {
    id: 2,
    name: 'Camera',
    url: 'camera.jpg',
    type: 'electronics',
    originalPrice: 600,
    price: 300,
    discount: 0.5,
    discountReason: 'RULE_MATCH(0,rule-id)',
    backend: BACKEND_INFO[backend],
  };
}

describe('App', () => {
  let client: MockLaunchDarklyClient;
  let http: HttpTestingController;

  beforeEach(() => {
    client = new MockLaunchDarklyClient({ [PURCHASE_FLAG]: false });
    useMockLaunchDarkly(client);
    TestBed.configureTestingModule({
      imports: [App],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    window.history.replaceState(null, '', '/');
  });

  afterEach(async () => {
    await closeLaunchDarkly();
    delete document.body.dataset['backend'];
  });

  function productRequest(backend: BackendId): TestRequest {
    return http.expectOne((req) => req.url.startsWith(`/api/${backend}/product/`));
  }

  async function render(backend: BackendId = 'spring-boot'): Promise<ComponentFixture<App>> {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    productRequest(backend).flush(camera(backend));
    await fixture.whenStable();
    return fixture;
  }

  const text = (fixture: ComponentFixture<App>) =>
    (fixture.nativeElement as HTMLElement).textContent ?? '';

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

  describe('backend routing with api-backend', () => {
    it('calls Spring Boot when the flag is missing, and says so', async () => {
      const fixture = await render('spring-boot');

      expect(text(fixture)).toContain('Served by Spring Boot 4.1.2');
      expect(text(fixture)).toContain('spring-boot · ERROR (FLAG_NOT_FOUND)');
      expect(document.body.dataset['backend']).toBe('spring-boot');
    });

    it('calls the backend the flag picks, with the routing header', async () => {
      client.setFlags({ [BACKEND_FLAG]: 'quarkus' });
      const fixture = TestBed.createComponent(App);
      await fixture.whenStable();

      const request = productRequest('quarkus');
      expect(request.request.headers.get('X-Backend-Routing')).toBe('flag:FALLTHROUGH');
      request.flush(camera('quarkus'));
      await fixture.whenStable();

      expect(text(fixture)).toContain('Served by Quarkus 3.40.1.1');
      expect(text(fixture)).toContain('⚡ Quarkus 3.40.1.1');
      expect(document.body.dataset['backend']).toBe('quarkus');
      http.verify();
    });

    it('switches backend live when the flag changes, refetching the same product', async () => {
      const fixture = TestBed.createComponent(App);
      await fixture.whenStable();
      const first = productRequest('spring-boot');

      // The flag changes while Spring Boot hasn't answered yet.
      client.setFlags({ [BACKEND_FLAG]: 'quarkus' });
      await fixture.whenStable();

      expect(first.cancelled).toBe(true);
      const second = productRequest('quarkus');
      expect(second.request.url).toBe(first.request.url.replace('spring-boot', 'quarkus'));
      second.flush(camera('quarkus'));
      await fixture.whenStable();
      expect(text(fixture)).toContain('Served by Quarkus 3.40.1.1');
    });

    it('keeps the current backend when the flag briefly evaluates to an error', async () => {
      client.setFlags({ [BACKEND_FLAG]: 'quarkus' });
      const fixture = await render('quarkus');

      // What the SDK reports for a moment while identify() or a reconnect loads flags.
      client.removeFlags(BACKEND_FLAG);
      await fixture.whenStable();

      http.expectNone((req) => req.url.startsWith('/api/spring-boot/'));
      expect(text(fixture)).toContain('Served by Quarkus 3.40.1.1');
    });

    it('does not identify again when refetching the same product', async () => {
      const identify = vi.spyOn(client, 'identify');
      const fixture = await render('spring-boot');

      client.setFlags({ [BACKEND_FLAG]: 'quarkus' });
      await fixture.whenStable();
      productRequest('quarkus').flush(camera('quarkus'));
      await fixture.whenStable();

      expect(identify).toHaveBeenCalledOnce();
    });

    it('lets ?backend= override the flag, and labels it', async () => {
      window.history.replaceState(null, '', '/?backend=quarkus');
      const fixture = TestBed.createComponent(App);
      await fixture.whenStable();

      const request = productRequest('quarkus');
      expect(request.request.headers.get('X-Backend-Routing')).toBe('url-override');
      request.flush(camera('quarkus'));
      await fixture.whenStable();
      expect(text(fixture)).toContain('quarkus · overridden by URL');
    });

    it('shows the backend as unavailable when it does not answer', async () => {
      client.setFlags({ [BACKEND_FLAG]: 'quarkus' });
      const fixture = TestBed.createComponent(App);
      await fixture.whenStable();

      productRequest('quarkus').error(new ProgressEvent('error'));
      await fixture.whenStable();

      expect(text(fixture)).toContain('Quarkus unavailable');
      expect(text(fixture)).toContain('⚡ Quarkus · unavailable');
      expect(text(fixture)).toContain('Could not load a product from Quarkus');
    });
  });
});
