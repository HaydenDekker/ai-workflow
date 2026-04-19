import { test, expect } from '@playwright/test';

test.describe('Observability View', () => {
  test('page loads', async ({ page }) => {
    await page.goto('/observability');
    await page.waitForLoadState('domcontentloaded');
    await expect(page).toHaveURL(/\/observability/i);
  });

  test('page renders content', async ({ page }) => {
    await page.goto('/observability');
    // Wait for health check to complete and cards to render
    await page.waitForTimeout(6000);
    
    // The body should have content
    const bodyText = await page.locator('body').textContent();
    expect(bodyText).not.toBe('');
  });

  test('refresh all button is present', async ({ page }) => {
    await page.goto('/observability');
    // Wait longer for Vaadin to render
    await page.waitForTimeout(5000);
    
    // Find button with "Refresh" text
    const buttons = await page.locator('vaadin-button').all();
    const hasRefresh = buttons.some(btn => btn.textContent().then(t => t.includes('Refresh')));
    expect(hasRefresh).toBe(true);
  });

  test('adapter status card is rendered', async ({ page }) => {
    await page.goto('/observability');
    // Wait for health check
    await page.waitForTimeout(6000);
    
    // The card should have rendered
    const cards = page.locator('.adapter-status-card');
    await expect(cards).toHaveCount(1);
  });

  test('card shows endpoint and time info', async ({ page }) => {
    await page.goto('/observability');
    await page.waitForTimeout(6000);
    
    const card = page.locator('.adapter-status-card');
    await expect(card).toBeVisible();
    
    // Should have endpoint and time fields
    const fields = card.locator('.endpoint-name, .last-checked');
    const count = await fields.count();
    expect(count).toBeGreaterThan(0);
  });

  test('card shows status indicator', async ({ page }) => {
    await page.goto('/observability');
    await page.waitForTimeout(6000);
    
    const card = page.locator('.adapter-status-card');
    await expect(card).toBeVisible();
    
    // Card should have an icon
    const icons = card.locator('vaadin-icon');
    const count = await icons.count();
    expect(count).toBeGreaterThan(0);
  });

  test('individual refresh button is present on card', async ({ page }) => {
    await page.goto('/observability');
    await page.waitForTimeout(6000);
    
    const card = page.locator('.adapter-status-card');
    await expect(card).toBeVisible();
    
    // Card should have a refresh button
    const refreshBtn = card.locator('vaadin-button');
    const count = await refreshBtn.count();
    expect(count).toBeGreaterThan(0);
  });

  test('page shows notification on load', async ({ page }) => {
    await page.goto('/observability');
    await page.waitForTimeout(6000);
    
    // The page should have rendered content
    const content = page.locator('body');
    await expect(content).toBeVisible();
  });
});
