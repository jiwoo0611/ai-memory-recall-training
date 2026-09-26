import { defineConfig } from "@playwright/test";
import path from "node:path";
import { fileURLToPath } from "node:url";
const frontend = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(frontend, "..");
const wrapper = process.platform === "win32" ? "gradlew.bat" : "./gradlew";
export default defineConfig({
  testDir: "./tests",
  testMatch: "**/*.spec.js",
  workers: 1,
  timeout: 30000,
  use: {
    baseURL: "http://localhost:5173",
    browserName: "chromium",
    trace: "off",
  },
  webServer: [
    {
      command: `${wrapper} --gradle-user-home .gradle-home --no-daemon --offline --init-script frontend/tests/backend.init.gradle frontendTestServer`,
      cwd: root,
      url: "http://127.0.0.1:18080",
      timeout: 180000,
      reuseExistingServer: false,
    },
    {
      command: "npm run dev",
      cwd: frontend,
      url: "http://localhost:5173",
      env: { VITE_API_BASE_URL: "http://127.0.0.1:18080" },
      timeout: 60000,
      reuseExistingServer: false,
    },
  ],
});
