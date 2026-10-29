import type { LDEvaluationDetailTyped } from '@launchdarkly/js-client-sdk';

/**
 * The api-backend flag picks which backend serves products. Its variations are names, never URLs:
 * client-side flag values are visible to anyone, so the browser maps each name to a path here, and
 * anything unexpected falls back to Spring Boot.
 */
export const BACKEND_FLAG = 'api-backend';

export type BackendId = 'spring-boot' | 'quarkus';

// Used if LaunchDarkly can't be reached or the flag is missing: the backend that's always been there.
export const DEFAULT_BACKEND: BackendId = 'spring-boot';

// A product request waits at most this long. A backend that doesn't answer shows "unavailable"
// instead of holding the page.
export const PRODUCT_TIMEOUT_MS = 2000;

export interface BackendInfo {
  name: string;
  emoji: string;
  /** Proxied to that backend's /api by src/proxy.conf.mjs. */
  apiPath: string;
}

export const BACKENDS: Record<BackendId, BackendInfo> = {
  'spring-boot': { name: 'Spring Boot', emoji: '🌱', apiPath: '/api/spring-boot' },
  quarkus: { name: 'Quarkus', emoji: '⚡', apiPath: '/api/quarkus' },
};

function isBackendId(value: unknown): value is BackendId {
  return typeof value === 'string' && value in BACKENDS;
}

export interface BackendChoice {
  id: BackendId;
  /** Sent as X-Backend-Routing, so the backend's log shows why the request came to it. */
  routing: string;
  /** For the api-backend line under the button. */
  description: string;
}

/**
 * ?backend=quarkus (or spring-boot) in the URL overrides the flag, so a developer can try a backend
 * without changing targeting. The UI labels it, so it's never mistaken for the flag's choice.
 */
export function urlOverride(search: string): BackendId | null {
  const value = new URLSearchParams(search).get('backend');
  return isBackendId(value) ? value : null;
}

export function chooseBackend(
  flag: LDEvaluationDetailTyped<string>,
  override: BackendId | null,
): BackendChoice {
  if (override) {
    return {
      id: override,
      routing: 'url-override',
      description: `${override} · overridden by URL`,
    };
  }
  const reason = flag.reason?.kind ?? 'UNKNOWN';
  const id = isBackendId(flag.value) ? flag.value : DEFAULT_BACKEND;
  const why = reason === 'ERROR' ? `ERROR (${flag.reason?.errorKind})` : reason;
  return { id, routing: `flag:${reason}`, description: `${id} · ${why}` };
}
