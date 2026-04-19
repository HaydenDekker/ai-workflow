import { chromium } from '@playwright/test';
import { spawn } from 'node:child_process';
import { join, dirname } from 'node:path';
import { promisify } from 'node:util';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

const sleep = promisify(setTimeout);

/**
 * Global setup: starts the Spring Boot dev server before tests run.
 *
 * Waits for the server to be ready on localhost:8080 before returning.
 */
export default async function globalSetup() {
  const projectRoot = join(__dirname, '..');
  const serverProcess = spawn(
    'cmd',
    ['/c', 'mvnw.cmd', 'spring-boot:run', '-Dspring-boot.run.fallback=false'],
    {
      cwd: projectRoot,
      stdio: 'pipe',
      shell: true,
      env: { ...process.env, PORT: '8080' },
    }
  );

  // Store on global for teardown
  (globalThis as Record<string, unknown>).SERVER_PROCESS = serverProcess;

  console.log('Waiting for Spring Boot dev server on port 8080...');

  // Wait for server to be ready (poll the root URL)
  const browser = await chromium.launch();
  const page = await browser.newPage();

  let ready = false;
  const maxAttempts = 60; // 5 minutes max
  for (let i = 0; i < maxAttempts; i++) {
    try {
      const response = await page.goto('http://localhost:8080', {
        waitUntil: 'domcontentloaded',
        timeout: 5000,
      });
      if (response && response.status() === 200) {
        ready = true;
        break;
      }
    } catch {
      // Server not ready yet
    }
    await sleep(5000);
  }

  await browser.close();

  if (!ready) {
    serverProcess.kill('SIGTERM');
    throw new Error('Spring Boot dev server failed to start within 5 minutes');
  }

  console.log('Spring Boot dev server is ready on port 8080');
}
