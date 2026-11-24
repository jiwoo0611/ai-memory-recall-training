import { defineConfig } from '@playwright/test';
export default defineConfig({
  testDir: './tests', testMatch: 'delayed-recall.spec.js', workers: 1,
  use: { baseURL: 'http://localhost:5176', browserName: 'chromium', channel: 'msedge' },
  webServer: { command: 'node node_modules/vite/bin/vite.js --host localhost --port 5176 --strictPort', url: 'http://localhost:5176', reuseExistingServer: false },
});
