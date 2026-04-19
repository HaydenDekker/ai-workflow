import { test, expect } from '@playwright/test';

test.describe('Agent List View', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/agents');
  });

  test('page loads with correct title', async ({ page }) => {
    await expect(page).toHaveTitle(/Agent List/i);
  });

  test('page title heading is visible', async ({ page }) => {
    const heading = page.getByRole('heading', { name: 'Agent List' });
    await expect(heading).toBeVisible();
  });

  test('grid component is rendered', async ({ page }) => {
    // Vaadin Grid renders as a table-like element
    const grid = page.locator('vaadin-grid');
    await expect(grid).toBeVisible();
  });

  test('refresh button is present', async ({ page }) => {
    const refreshButton = page.getByRole('button', { name: /refresh/i });
    await expect(refreshButton).toBeVisible();
  });

  test('grid displays agent columns', async ({ page }) => {
    // Wait for grid to populate
    await page.waitForTimeout(2000);

    // Check for column headers
    const columns = [
      /ID/i,
      /Title/i,
      /Agent Type/i,
      /File Regex/i,
      /Source/i,
      /Created/i,
      /Active/i,
    ];

    for (const col of columns) {
      const header = page.locator('th', { hasText: col });
      // Headers may be in the grid header or as part of the grid component
      await expect(header).toHaveCount(1);
    }
  });

  test('shows notification on load', async ({ page }) => {
    // Waadin notifications appear as toast messages
    const notification = page.locator('vaadin-notification-card');
    // Either a success message or "No agents found" message should appear
    await expect(notification).toBeVisible({ timeout: 10_000 });
  });
});
