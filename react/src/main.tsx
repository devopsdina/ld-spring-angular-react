import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';

import App from './App.tsx';
import { applicationInfo } from './app-info';
import { loadBootstrap, userPart } from './bootstrap';
import { createLaunchDarklyProvider } from './launchdarkly/launchdarkly';
import { user, userContext } from './user';

// Waits at most 500ms for the bootstrap. Without one, the SDK starts the usual way.
const bootstrap = await loadBootstrap(user);

// Set VITE_LD_CLIENT_SIDE_ID in .env.local (see .env.example).
const LaunchDarklyProvider = createLaunchDarklyProvider(
  import.meta.env.VITE_LD_CLIENT_SIDE_ID,
  bootstrap?.context ?? userContext,
  { applicationInfo },
  bootstrap?.flags,
);

// identify() adds the product to this same user once the product loads.
const userAttributes = bootstrap ? userPart(bootstrap.context) : undefined;

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <LaunchDarklyProvider>
      <App userAttributes={userAttributes} />
    </LaunchDarklyProvider>
  </StrictMode>,
);
