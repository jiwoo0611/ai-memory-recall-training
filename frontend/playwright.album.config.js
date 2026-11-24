import { defineConfig } from '@playwright/test';
export default defineConfig({
  testDir: './tests', testMatch: 'album-upload.spec.js', workers: 1,
  use: { baseURL: 'http://localhost:5177', browserName: 'chromium',
    ...(process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH ? { launchOptions: { executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH } } : {}) },
  webServer: { command: 'npm run dev -- --port 5177', url: 'http://localhost:5177', reuseExistingServer: false },
});
