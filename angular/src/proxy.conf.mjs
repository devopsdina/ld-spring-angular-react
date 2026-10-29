// /api/spring-boot/* and /api/quarkus/* go to each backend's /api/*, so the browser never makes a
// cross-origin call and the backends need no CORS settings. Docker Compose sets both URLs.
const backend = (target, prefix) => ({
  target,
  secure: false,
  pathRewrite: { [`^/api/${prefix}`]: '/api' },
});

export default {
  '/api/spring-boot': backend(
    process.env.SPRING_BOOT_URL ?? 'http://localhost:8080',
    'spring-boot',
  ),
  '/api/quarkus': backend(process.env.QUARKUS_URL ?? 'http://localhost:8081', 'quarkus'),
};
