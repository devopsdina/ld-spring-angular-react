import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';

import App from './App.tsx';
import { applicationInfo } from './app-info';
import { createLaunchDarklyProvider } from './launchdarkly/launchdarkly';
import { userContext } from './user';

// Set VITE_LD_CLIENT_SIDE_ID in .env.local (see .env.example).
const LaunchDarklyProvider = createLaunchDarklyProvider(
  import.meta.env.VITE_LD_CLIENT_SIDE_ID,
  userContext,
  { applicationInfo },
);

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <LaunchDarklyProvider>
      <App />
    </LaunchDarklyProvider>
  </StrictMode>,
);
