import type { LDContextCommon } from '@launchdarkly/react-sdk';

import type { DemoUser } from './user';

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

// /api is proxied to the Spring Boot server (see vite.config.ts).
export async function getProduct(id: number, user: DemoUser): Promise<ProductOffer> {
  const params = new URLSearchParams({
    userKey: user.key,
    email: user.email,
    plan: user.plan,
    framework: user.framework,
  });
  const response = await fetch(`/api/product/${id}?${params}`);
  if (!response.ok) {
    throw new Error(`GET /api/product/${id} returned ${response.status}`);
  }
  return response.json();
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
