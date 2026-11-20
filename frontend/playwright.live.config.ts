import { defineConfig, devices } from '@playwright/test'

/**
 * End-to-end against the real backend: `npm run test:e2e:live`.
 * Needs the backend running on http://localhost:8080 with the dev profile and freshly seeded demo
 * data (docs/api/README.md → "Resetting the demo data"), because the tests change that data.
 */
const PORT = 5174

export default defineConfig({
  testDir: './e2e-live',
  fullyParallel: false,
  workers: 1,
  reporter: 'list',
  use: {
    baseURL: `http://localhost:${PORT}`,
    trace: 'retain-on-failure',
  },
  // Uses the Chrome already installed on the machine, so no browser download is needed.
  projects: [{ name: 'chrome', use: { ...devices['Desktop Chrome'], channel: 'chrome' } }],
  webServer: {
    command: `npx vite --port ${PORT} --strictPort`,
    url: `http://localhost:${PORT}`,
    reuseExistingServer: false,
    timeout: 120_000,
    env: { VITE_USE_MOCKS: 'false', VITE_API_BASE_URL: '/api/v1' },
  },
})
