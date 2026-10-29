import type { LDContext, LDContextCommon } from '@launchdarkly/react-sdk';

import { BACKENDS, DEFAULT_BACKEND } from './backend';
import type { DemoUser } from './user';

/**
 * The SDK init budget. Fetching the bootstrap counts against it: if the backend doesn't answer in
 * time, the app starts without a bootstrap, the way it would anyway.
 */
export const BOOTSTRAP_TIMEOUT_MS = 500;

export interface Bootstrap {
  /** The context the server evaluated the flags for. Start the SDK with exactly this context. */
  context: LDContext;
  /** allFlagsState() from the server SDK, for the React SDK's bootstrap option. */
  flags: unknown;
}

/**
 * Gets the user's context and client-side flags from Spring Boot, so the first render already has
 * flag values: no flicker, and the first product request goes to the backend api-backend picks.
 * The browser sends the user's values (here, from the URL); the server builds the context. Returns
 * undefined if the backend is down, slow, or not connected to LaunchDarkly yet.
 */
export async function loadBootstrap(user: DemoUser): Promise<Bootstrap | undefined> {
  const params = new URLSearchParams({
    email: user.email,
    plan: user.plan,
    framework: user.framework,
  });
  const timeout = new AbortController();
  const timer = setTimeout(() => timeout.abort(), BOOTSTRAP_TIMEOUT_MS);
  try {
    const response = await fetch(`${BACKENDS[DEFAULT_BACKEND].apiPath}/flags/bootstrap?${params}`, {
      signal: timeout.signal,
    });
    return response.ok ? await response.json() : undefined;
  } catch {
    return undefined;
  } finally {
    clearTimeout(timer);
  }
}

/** The bootstrap context's attributes without its kind, for the user part of a multi-context. */
export function userPart(context: LDContext): LDContextCommon {
  const { kind: _kind, ...attributes } = context as LDContextCommon & { kind: string };
  return attributes;
}
