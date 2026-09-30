import { defineConfig } from "@playwright/test";
export default defineConfig({
  testDir: "./tests",
  testMatch: "recall.spec.js",
  workers: 1,
  timeout: 45000,
  use: { baseURL: "http://localhost:5173", browserName: "chromium", channel: "msedge", viewport: { width: 1440, height: 1000 }, screenshot: "only-on-failure" },
});
