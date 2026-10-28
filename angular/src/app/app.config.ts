import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient, withFetch } from '@angular/common/http';

import { clientSideID } from '../environments/environment';
import { applicationInfo } from './app-info';
import { provideLaunchDarklyClient } from './launchdarkly/flag-signal';
import { userContext } from './user';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideHttpClient(withFetch()),
    // Creates the one client for the page, with evaluation reasons on, and waits at most 500ms for
    // flags before the first render. The product isn't known yet, so this starts with the user;
    // App adds the product with identifyContext() once it loads.
    provideLaunchDarklyClient({ clientSideID, context: userContext, options: { applicationInfo } }),
  ],
};
