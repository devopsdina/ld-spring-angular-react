// The LaunchDarkly provider for the page, with the PS preflight checklist rules applied:
//
// 1. The SDK is initialized once, as a singleton. Calling createLaunchDarklyProvider() again logs
//    a warning and returns the existing provider instead of creating a second client.
//    https://launchdarkly-labs.github.io/ps-flag-book/sdk/preflight-checklist/init-and-config.html#sdk-is-initialized-once-as-a-singleton-early-in-the-applications-lifecycle
//
// 2. Initialization never blocks for more than 500ms. The provider renders immediately; hooks
//    return fallback values until flags load, then update.
//    https://launchdarkly-labs.github.io/ps-flag-book/sdk/preflight-checklist/init-and-config.html#application-does-not-block-indefinitely-for-initialization
//
// 3. identify() never blocks for more than 500ms either. Pass IDENTIFY_TIMEOUT_SECONDS.
//    https://launchdarkly-labs.github.io/ps-flag-book/cookbook/identify-timeout/index.html#javascript-client-side
//
// 4. Evaluation reasons are on (`withReasons: true`), so the *VariationDetail hooks report why a
//    flag served its value.
//    https://launchdarkly.com/docs/sdk/features/evaluation-reasons
import {
  createLDReactProvider,
  type LDContext,
  type LDReactClientOptions,
} from '@launchdarkly/react-sdk';
import type { FC, ReactNode } from 'react';

// SDK timeouts are in seconds: 0.5 = 500ms.
export const INIT_TIMEOUT_SECONDS = 0.5;
export const IDENTIFY_TIMEOUT_SECONDS = 0.5;

type LaunchDarklyProvider = FC<{ children: ReactNode }>;

let provider: LaunchDarklyProvider | null = null;

/** Creates the LaunchDarkly client and its React provider, once, and starts the client. */
export function createLaunchDarklyProvider(
  clientSideID: string,
  context: LDContext,
  options: LDReactClientOptions = {},
): LaunchDarklyProvider {
  if (provider) {
    console.warn(
      '[LaunchDarkly] createLaunchDarklyProvider() was called again. Reusing the existing client; the SDK is not re-initialized.',
    );
    return provider;
  }
  provider = createLDReactProvider(clientSideID, context, {
    // Streaming keeps flags live, so hooks re-render when a flag changes in LaunchDarkly.
    ldOptions: { streaming: true, withReasons: true, ...options },
    startOptions: { timeout: INIT_TIMEOUT_SECONDS },
  });
  return provider;
}
