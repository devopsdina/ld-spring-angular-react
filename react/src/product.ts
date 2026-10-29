import type { LDContextCommon } from '@launchdarkly/react-sdk';

import { BACKENDS, type BackendChoice, type BackendId, PRODUCT_TIMEOUT_MS } from './backend';
import type { DemoUser } from './user';

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

/**
 * Gets a product from the chosen backend (proxied by vite.config.ts). Gives up after
 * PRODUCT_TIMEOUT_MS, or when `signal` aborts because the backend changed.
 */
export async function getProduct(
  id: number,
  user: DemoUser,
  backend: BackendChoice,
  signal: AbortSignal,
): Promise<ProductOffer> {
  const params = new URLSearchParams({
    userKey: user.key,
    email: user.email,
    plan: user.plan,
    framework: user.framework,
  });
  const timeout = new AbortController();
  const timer = setTimeout(() => timeout.abort(), PRODUCT_TIMEOUT_MS);
  const abort = () => timeout.abort();
  signal.addEventListener('abort', abort);
  try {
    const response = await fetch(`${BACKENDS[backend.id].apiPath}/product/${id}?${params}`, {
      headers: { 'X-Backend-Routing': backend.routing },
      signal: timeout.signal,
    });
    if (!response.ok) {
      throw new Error(`GET /product/${id} returned ${response.status}`);
    }
    return await response.json();
  } finally {
    clearTimeout(timer);
    signal.removeEventListener('abort', abort);
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
