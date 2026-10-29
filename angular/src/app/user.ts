import type { LDContextCommon, LDSingleKindContext } from '@launchdarkly/js-client-sdk';

// The demo user. Set email and plan in the URL to try targeting rules, for example
// http://localhost:4200/?email=you@example.com&plan=gold
const params = new URLSearchParams(window.location.search);

export interface DemoUser {
  key: string;
  email: string;
  plan: string;
  framework: 'angular';
}

const email = params.get('email') || 'abc123@gmail.com';

export const user: DemoUser = {
  key: email,
  email,
  plan: params.get('plan') || 'silver',
  framework: 'angular',
};

/** The user's attributes, for use inside a multi-context. */
export const userAttributes: LDContextCommon = { ...user };

export const userContext: LDSingleKindContext = { kind: 'user', ...userAttributes };
