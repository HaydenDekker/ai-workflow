import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright configuration for E2E tests.
 *
 * Tests the Vaadin/Hilla UI by starting the Spring Boot dev server
 * and verifying page rendering in a real Chromium browser.
 *
 * Usage:
 *   npx playwright test                    # Run all tests
 *   npx playwright test --headed           # Show browser
 *   npx playwright test tests/e2e/agents   # Run specific test file
 */
export default defineConfig({
  testDir: './tests/e2e',

  // Timeout for each test (30s is enough for Spring Boot startup)
  timeout: 60_000,

  // Retry failed tests once (transient startup issues)
  retries: 1,

  // Report to HTML on failure
  reporter: [['html', { open: 'never' }]],

  use: {
    // Base URL for all tests (set via PLAYWRIGHT_BASE_URL env var or below)
    baseURL: process.env.PLAYWRIGHT_BASE_URL || 'http://localhost:8080',

    // Take screenshot on failure
    screenshot: 'only-on-failure',

    // Take trace on failure
    trace: 'retain-on-failure',

    // Video on failure
    video: 'retain-on-failure',
  },

  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],

  // Global setup: start the Spring Boot dev server
  globalSetup: './tests/global-setup.ts',

  // Global teardown: stop the dev server
  globalTeardown: './tests/global-teardown.ts',
});
