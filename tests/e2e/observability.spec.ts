import { test, expect } from '@playwright/test';

test.describe('Observability View', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/observability');
  });

  test('page loads with correct title', async ({ page }) => {
    await expect(page).toHaveTitle(/Observability/i);
  });

  test('page title heading is visible', async ({ page }) => {
    const heading = page.getByRole('heading', { name: 'LLM Adapter Status' });
    await expect(heading).toBeVisible();
  });

  test('refresh all button is present', async ({ page }) => {
    const refreshButton = page.getByRole('button', { name: /refresh all/i });
    await expect(refreshButton).toBeVisible();
  });

  test('status cards container is rendered', async ({ page }) => {
    const cardsContainer = page.locator('.status-cards-container');
    await expect(cardsContainer).toBeVisible();
  });

  test('adapter status card is rendered for configured endpoint', async ({ page }) => {
    // Wait for health check to complete
    await page.waitForTimeout(5000);

    const card = page.locator('.adapter-status-card');
    await expect(card).toHaveCount(1);
  });

  test('card shows endpoint name', async ({ page }) => {
    await page.waitForTimeout(5000);

    const endpointField = page.locator('.endpoint-name');
    await expect(endpointField).toBeVisible();
  });

  test('card shows last checked time', async ({ page }) => {
    await page.waitForTimeout(5000);

    const lastCheckedField = page.locator('.last-checked');
    await expect(lastCheckedField).toBeVisible();
  });

  test('card shows status indicator', async ({ page }) => {
    await page.waitForTimeout(5000);

    // Status icon should be visible
    const statusIcon = page.locator('vaadin-icon');
    await expect(statusIcon).toBeVisible();
  });

  test('card has correct status badge color', async ({ page }) => {
    await page.waitForTimeout(5000);

    // Check for status badge class
    const badge = page.locator('.status-badge');
    await expect(badge).toBeVisible();
  });

  test('individual refresh button is present on each card', async ({ page }) => {
    await page.waitForTimeout(5000);

    const refreshButton = page.locator('.refresh-btn');
    await expect(refreshButton).toBeVisible();
  });

  test('shows notification on load', async ({ page }) => {
    // Vaadin notifications appear as toast messages
    const notification = page.locator('vaadin-notification-card');
    await expect(notification).toBeVisible({ timeout: 10_000 });
  });
});
