/**
 * CLI utility to capture screenshots of application views using Playwright.
 *
 * Usage:
 *   npx tsx scripts/capture-snapshot.ts /observability
 *   npx tsx scripts/capture-snapshot.ts /agents --wait 5000
 *   npx tsx scripts/capture-snapshot.ts /observability --output custom-name.png
 *   npx tsx scripts/capture-snapshot.ts /observability --viewport 1280x720
 *
 * Features:
 * - Starts Spring Boot dev server automatically if not running
 * - Waits for Vaadin to fully render (health checks, cards, etc.)
 * - Saves screenshot to project/screenshots/
 * - Cleans up server on exit
 */

import { chromium, Browser, Page } from '@playwright/test';
import { spawn, ChildProcess } from 'node:child_process';
import { join, dirname, extname } from 'node:path';
import { promisify } from 'node:util';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const sleep = promisify(setTimeout);

interface Args {
  url: string;
  output?: string;
  wait: number;
  viewport?: string;
}

function parseArgs(argv: string[]): Args {
  const args: Args = {
    url: '',
    wait: 8000,
  };

  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    if (arg.startsWith('--')) {
      const key = arg.slice(2);
      const value = argv[i + 1];
      switch (key) {
        case 'output':
          args.output = value;
          i++;
          break;
        case 'wait':
          args.wait = parseInt(value, 10) || 8000;
          i++;
          break;
        case 'viewport':
          args.viewport = value;
          i++;
          break;
        default:
          console.error(`Unknown option: --${key}`);
          process.exit(1);
      }
    } else if (!args.url) {
      // Strip Windows drive letter/path prefix (e.g. C:/Program Files/Git/observability → /observability)
      let cleaned = arg;
      // Handle WSL/Git Bash paths like C:/Program Files/Git/observability
      const winPathMatch = arg.match(/^[A-Z]:[\\/].*/);
      if (winPathMatch) {
        // Extract just the last segment after the last slash
        const parts = arg.split(/[\\/]/);
        cleaned = '/' + parts[parts.length - 1];
      } else if (!cleaned.startsWith('/')) {
        cleaned = '/' + cleaned;
      }
      args.url = cleaned;
    }
  }

  if (!args.url) {
    console.error('Usage: npx tsx scripts/capture-snapshot.ts <url-path> [options]');
    console.error('');
    console.error('Options:');
    console.error('  --output <name>       Output filename (default: <slug>.png)');
    console.error('  --wait <ms>           Wait time before capture (default: 8000)');
    console.error('  --viewport <WxH>      Browser viewport size (e.g. 1280x720)');
    console.error('');
    console.error('Examples:');
    console.error('  npx tsx scripts/capture-snapshot.ts /observability');
    console.error('  npx tsx scripts/capture-snapshot.ts /agents --wait 5000');
    process.exit(1);
  }

  return args;
}

function slugify(url: string): string {
  return url.replace(/^\//, '').replace(/[^a-zA-Z0-9]/g, '-') || 'home';
}

async function waitForServer(page: Page, maxAttempts: number = 60): Promise<boolean> {
  for (let i = 0; i < maxAttempts; i++) {
    try {
      const res = await page.goto('http://localhost:8080', {
        waitUntil: 'domcontentloaded',
        timeout: 5000,
      });
      if (res && res.status() === 200) {
        return true;
      }
    } catch {
      // Server not ready yet
    }
    await sleep(5000);
  }
  return false;
}

async function main() {
  const argv = process.argv.slice(2);
  const args = parseArgs(argv);

  // scripts/ is one level deep, so go up one to get project root
  const projectRoot = join(__dirname, '..');
  const screenshotDir = join(projectRoot, 'project', 'screenshots');

  // Determine output path
  const defaultName = `${slugify(args.url)}.png`;
  const outputName = args.output || defaultName;
  const outputPath = join(screenshotDir, outputName);

  console.log(`Starting Spring Boot dev server...`);
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

  let browser: Browser | null = null;

  try {
    browser = await chromium.launch();
    const page = await browser.newPage();

    // Wait for server to be ready
    console.log(`Waiting for Spring Boot dev server on port 8080...`);
    const ready = await waitForServer(page);
    if (!ready) {
      serverProcess.kill('SIGTERM');
      await browser.close();
      throw new Error('Spring Boot dev server failed to start within 5 minutes');
    }
    console.log('Spring Boot dev server is ready on port 8080');

    // Navigate to the view
    console.log(`Navigating to ${args.url}...`);
    await page.goto(`http://localhost:8080${args.url}`, {
      waitUntil: 'networkidle',
      timeout: 30000,
    });

    // Set viewport if specified
    if (args.viewport) {
      const [width, height] = args.viewport.split('x').map(Number);
      if (width && height) {
        await page.setViewportSize({ width, height });
        console.log(`Viewport set to ${width}x${height}`);
      }
    }

    // Wait for rendering
    console.log(`Waiting ${args.wait}ms for view to render...`);
    await sleep(args.wait);

    // Capture screenshot
    await page.screenshot({
      path: outputPath,
      fullPage: false,
    });
    console.log(`Screenshot saved to: ${outputPath}`);
  } catch (err) {
    console.error('Error capturing snapshot:', err);
    process.exit(1);
  } finally {
    // Cleanup
    if (browser) {
      await browser.close();
    }
    serverProcess.kill('SIGTERM');
    console.log('Server stopped.');
  }
}

main().catch((err) => {
  console.error('Fatal error:', err);
  process.exit(1);
});
