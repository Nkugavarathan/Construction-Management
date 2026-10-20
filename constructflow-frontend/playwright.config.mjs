import { defineConfig, devices } from "@playwright/test";

const baseURL = process.env.E2E_BASE_URL ?? "http://localhost:3000";
const port = new URL(baseURL).port || "3000";

/**
 * End-to-end tests against the real stack with the local seed data:
 *   1. docker compose up -d postgres
 *   2. backend:  ./mvnw spring-boot:run -Dspring-boot.run.profiles=local   (seeds demo data on an empty DB)
 *   3. npm run build && npm run test:e2e   (starts the frontend on :3000 if it is not already running)
 * Tests create their own uniquely named records, so they can run repeatedly against the same database.
 */
export default defineConfig({
  testDir: "./e2e",
  timeout: 60_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    baseURL,
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
  webServer: {
    command: `npx next start -p ${port}`,
    url: `${baseURL}/login`,
    reuseExistingServer: true,
    timeout: 120_000,
  },
});
