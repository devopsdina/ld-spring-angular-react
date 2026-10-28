import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import type { LDContextCommon } from '@launchdarkly/js-client-sdk';
import { Observable } from 'rxjs';

import { DemoUser } from './user';

/** A product as the Spring Boot API returns it, after the server-side discount-pricing flag. */
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
}

@Injectable({ providedIn: 'root' })
export class ProductService {
  private readonly http = inject(HttpClient);

  // /api is proxied to the Spring Boot server (see src/proxy.conf.json).
  getProduct(id: number, user: DemoUser): Observable<ProductOffer> {
    return this.http.get<ProductOffer>(`/api/product/${id}`, {
      params: { userKey: user.key, email: user.email, plan: user.plan, framework: user.framework },
    });
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
