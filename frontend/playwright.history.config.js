import { defineConfig } from "@playwright/test";

// Isolated browser regression tests: API fixtures, no database or external AI calls.
export default defineConfig({
  testDir: "./tests",
  testMatch: "history.spec.js",
  workers: 1,
  use: { baseURL: "http://localhost:5174", browserName: "chromium" },
  webServer: {
    command: "npm run dev -- --port 5174",
    url: "http://localhost:5174",
    reuseExistingServer: false,
  },
});
