// Sends /api to the Spring Boot server. Docker Compose sets API_PROXY_TARGET to http://server:8080.
export default {
  '/api': {
    target: process.env.API_PROXY_TARGET ?? 'http://localhost:8080',
    secure: false,
  },
};
