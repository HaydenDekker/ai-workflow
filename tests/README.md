# Playwright E2E Tests

End-to-end tests for the Vaadin/Hilla UI using Playwright.

## Prerequisites

- Node.js installed
- `@playwright/test` installed (`npm install`)
- Chromium browser installed (`npx playwright install chromium`)

## Running Tests

### Run all E2E tests (headless)

```bash
npm run test:e2e
```

This starts the Spring Boot dev server, runs all tests, then stops the server.

### Run with visible browser

```bash
npm run test:e2e:headed
```

### Run in UI mode (interactive)

```bash
npm run test:e2e:ui
```

Opens the Playwright UI for running individual tests, debugging, and viewing traces.

### Run specific test file

```bash
npx playwright test tests/e2e/agents.spec.ts
```

## Test Structure

```
tests/
├── global-setup.ts          # Starts Spring Boot dev server
├── global-teardown.ts       # Stops Spring Boot dev server
└── e2e/
    ├── agents.spec.ts       # Agent List view tests
    └── observability.spec.ts # LLM Observability dashboard tests
```

## What Tests Verify

### Agent List (`agents.spec.ts`)

- Page title renders correctly
- "Agent List" heading is visible
- Grid component is rendered
- Refresh button is present
- Grid displays all expected column headers
- Notification appears on load

### Observability (`observability.spec.ts`)

- Page title renders correctly
- "LLM Adapter Status" heading is visible
- "Refresh All" button is present
- Status cards container is rendered
- Adapter status cards are displayed for configured endpoints
- Endpoint name, last checked time, and status badge are visible
- Individual card refresh buttons are present
- Notification appears on load

## Troubleshooting

### Server fails to start

The global setup waits up to 5 minutes for the Spring Boot dev server. If it times out:
- Check for port conflicts (`netstat -ano | findstr :8080`)
- Verify Maven is installed and `./mvnw.cmd` works
- Check console output for startup errors

### Tests fail with connection refused

- Ensure no other process is using port 8080
- Check if the Spring Boot app started successfully (look for global setup output)

### Stale browser state

Playwright tests are isolated by default. Each test gets a fresh browser context.
If tests fail intermittently, try running with `--retries=0` to rule out flakiness.
