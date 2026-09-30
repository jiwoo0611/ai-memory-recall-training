import { defineConfig } from "@playwright/test";
import path from "node:path";
import { fileURLToPath } from "node:url";
const frontend = path.dirname(fileURLToPath(import.meta.url));
export default defineConfig({
  testDir: "./tests", testMatch: "quiz-persistence.spec.js", workers: 1, timeout: 45000,
  use: { baseURL: "http://localhost:5174", browserName: "chromium", channel: "msedge", screenshot: "only-on-failure" },
  webServer: [
    { command: `${process.platform === "win32" ? "gradlew.bat" : "./gradlew"} --offline --no-daemon --init-script ../frontend/tests/quiz-backend.init.gradle quizBrowserTestServer`,
      cwd: path.resolve(frontend, "../backend"), url: "http://127.0.0.1:18081/api/users/me", timeout: 120000, reuseExistingServer: false },
    { command: "node node_modules/vite/bin/vite.js --host localhost --port 5174 --strictPort", cwd: frontend,
      url: "http://localhost:5174", env: { VITE_API_BASE_URL: "http://127.0.0.1:18081" }, timeout: 60000, reuseExistingServer: false },
  ],
});
