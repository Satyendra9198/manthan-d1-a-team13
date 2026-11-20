import { defineConfig, devices } from "@playwright/test";

/**
 * E2E against the running stack (nginx :8080 by default).
 *   E2E_BASE_URL   target URL (default http://localhost:8080)
 *   E2E_CHANNEL    optional browser channel fallback, e.g. "chrome" or "msedge"
 */
const baseURL = process.env.E2E_BASE_URL ?? "http://localhost:8080";
const channel = process.env.E2E_CHANNEL;

export default defineConfig({
  testDir: "./e2e",
  timeout: 60_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  workers: 1,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [["github"], ["html", { open: "never" }]] : [["list"], ["html", { open: "never" }]],
  use: {
    baseURL,
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
    locale: "en-IN",
    timezoneId: "Asia/Kolkata",
  },
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"], ...(channel ? { channel } : {}) },
    },
  ],
});
