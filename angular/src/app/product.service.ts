import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import type { LDContextCommon } from '@launchdarkly/js-client-sdk';
import { Observable, timeout } from 'rxjs';

import { BACKENDS, BackendChoice, BackendId, PRODUCT_TIMEOUT_MS } from './backend';
import { DemoUser } from './user';

/** A product as either backend returns it, after the server-side discount-pricing flag. */
export interface ProductOffer {
  id: number;
  name: string;
  url: string;
  type: string;
  originalPrice: number;
  price: number;
  discount: number;
  /** Why the server's discount-pricing flag served its value, e.g. FALLTHROUGH or RULE_MATCH. */
  discountReason: string;
  /** The backend that actually answered. */
  backend: { id: BackendId; name: string; version: string };
}

@Injectable({ providedIn: 'root' })
export class ProductService {
  private readonly http = inject(HttpClient);

  /**
   * Gets a product from the chosen backend (proxied by src/proxy.conf.mjs). Gives up after
   * PRODUCT_TIMEOUT_MS; unsubscribing, e.g. because the backend changed, cancels the request.
   */
  getProduct(id: number, user: DemoUser, backend: BackendChoice): Observable<ProductOffer> {
    return this.http
      .get<ProductOffer>(`${BACKENDS[backend.id].apiPath}/product/${id}`, {
        params: {
          userKey: user.key,
          email: user.email,
          plan: user.plan,
          framework: user.framework,
        },
        headers: { 'X-Backend-Routing': backend.routing },
      })
      .pipe(timeout(PRODUCT_TIMEOUT_MS));
  }
}

/**
 * The product's attributes for a `product` context, the same kind and attributes the server uses,
 * so one targeting rule (e.g. product type is "food") works for both the browser and server flags.
 */
export function productAttributes(product: ProductOffer): LDContextCommon {
  return {
    key: String(product.id),
    name: product.name,
    type: product.type,
    price: product.originalPrice,
  };
}
