/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

// /api/spring-boot/* and /api/quarkus/* go to each backend's /api/*, so the browser never makes a
// cross-origin call and the backends need no CORS settings. Docker Compose sets both URLs.
const backend = (target: string) => ({
  target,
  rewrite: (path: string) => path.replace(/^\/api\/(spring-boot|quarkus)/, '/api'),
});

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    proxy: {
      '/api/spring-boot': backend(process.env.SPRING_BOOT_URL ?? 'http://localhost:8080'),
      '/api/quarkus': backend(process.env.QUARKUS_URL ?? 'http://localhost:8081'),
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
  },
});
