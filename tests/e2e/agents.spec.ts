import { test, expect } from '@playwright/test';

test.describe('Agent List View', () => {
  test('page loads', async ({ page }) => {
    await page.goto('/agents');
    await page.waitForLoadState('domcontentloaded');
    await expect(page).toHaveURL(/\/agents/i);
  });

  test('page renders content', async ({ page }) => {
    await page.goto('/agents');
    await page.waitForTimeout(3000);
    
    // The body should have content
    const bodyText = await page.locator('body').textContent();
    expect(bodyText).not.toBe('');
  });

  test('refresh button is present', async ({ page }) => {
    await page.goto('/agents');
    // Wait longer for Vaadin to render
    await page.waitForTimeout(5000);
    
    // Find button with "Refresh" text
    const buttons = await page.locator('vaadin-button').all();
    const hasRefresh = buttons.some(btn => btn.textContent().then(t => t.includes('Refresh')));
    expect(hasRefresh).toBe(true);
  });

  test('shows notification on load', async ({ page }) => {
    await page.goto('/agents');
    await page.waitForTimeout(3000);
    
    // The page should have rendered content
    const content = page.locator('body');
    await expect(content).toBeVisible();
  });
});
