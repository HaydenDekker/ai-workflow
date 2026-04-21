import { test, expect } from '@playwright/test';

test.describe('Navigation', () => {
  test('top navigation bar is visible on home page', async ({ page }) => {
    await page.goto('/');
    await page.waitForTimeout(3000);

    // Check for navigation items in the navbar
    // Vaadin MenuBar renders items as buttons with text content
    const agentsLink = page.locator('vaadin-menu-bar-button', { hasText: 'Agents' }).first();
    const obsLink = page.locator('vaadin-menu-bar-button', { hasText: 'Observability' }).first();
    
    // At least one navigation link should be present
    const navCount = (await agentsLink.count()) + (await obsLink.count());
    expect(navCount).toBeGreaterThan(0);
  });

  test('Agents navigation link is visible and clickable', async ({ page }) => {
    await page.goto('/');
    await page.waitForTimeout(3000);

    // Open the menu bar
    const menuButton = page.locator('vaadin-menu-bar-button').first();
    await menuButton.click();
    await page.waitForTimeout(1000);

    // Look for Agents link in the submenu
    const agentsItem = page.locator('vaadin-item', { hasText: 'Agents' }).first();
    await expect(agentsItem).toBeVisible();

    // Click Agents to navigate
    await agentsItem.click();
    await page.waitForTimeout(3000);
    
    // Should navigate to /agents
    await expect(page).toHaveURL(/\/agents/i);
  });

  test('Observability navigation link is visible and clickable', async ({ page }) => {
    await page.goto('/');
    await page.waitForTimeout(3000);

    // Open the menu bar
    const menuButton = page.locator('vaadin-menu-bar-button').first();
    await menuButton.click();
    await page.waitForTimeout(1000);

    // Look for Observability link in the submenu
    const obsItem = page.locator('vaadin-item', { hasText: 'Observability' }).first();
    await expect(obsItem).toBeVisible();

    // Click Observability to navigate
    await obsItem.click();
    await page.waitForTimeout(3000);
    
    // Should navigate to /observability
    await expect(page).toHaveURL(/\/observability/i);
  });

  test('Agents view is accessible via direct URL', async ({ page }) => {
    await page.goto('/agents');
    await page.waitForTimeout(5000);
    
    // Should have the Agents page title
    await expect(page).toHaveURL(/\/agents/i);
    
    // Should have a page title element
    const pageTitle = page.locator('h2:has-text("Agent List")').first();
    await expect(pageTitle).toBeVisible();
  });

  test('Observability view is accessible via direct URL', async ({ page }) => {
    await page.goto('/observability');
    await page.waitForTimeout(5000);
    
    // Should have the Observability page title
    await expect(page).toHaveURL(/\/observability/i);
    
    // Should have a page title element
    const pageTitle = page.locator('h2:has-text("LLM Adapter Status")').first();
    await expect(pageTitle).toBeVisible();
  });

  test('home page shows welcome content', async ({ page }) => {
    await page.goto('/');
    await page.waitForTimeout(3000);
    
    // Should show welcome content
    const welcomeText = page.locator('h1:has-text("Welcome to AI Workflow")').first();
    await expect(welcomeText).toBeVisible();
  });

  test('can navigate from Agents to Observability via top nav', async ({ page }) => {
    await page.goto('/agents');
    await page.waitForTimeout(5000);
    
    // Open menu bar
    const menuButton = page.locator('vaadin-menu-bar-button').first();
    await menuButton.click();
    await page.waitForTimeout(1000);
    
    // Click Observability
    const obsItem = page.locator('vaadin-item', { hasText: 'Observability' }).first();
    await expect(obsItem).toBeVisible();
    await obsItem.click();
    await page.waitForTimeout(3000);
    
    // Should be on observability page
    await expect(page).toHaveURL(/\/observability/i);
    
    // Should show LLM Adapter Status heading
    const obsHeading = page.locator('h2:has-text("LLM Adapter Status")').first();
    await expect(obsHeading).toBeVisible();
  });

  test('can navigate from Observability to Agents via top nav', async ({ page }) => {
    await page.goto('/observability');
    await page.waitForTimeout(5000);
    
    // Open menu bar
    const menuButton = page.locator('vaadin-menu-bar-button').first();
    await menuButton.click();
    await page.waitForTimeout(1000);
    
    // Click Agents
    const agentsItem = page.locator('vaadin-item', { hasText: 'Agents' }).first();
    await expect(agentsItem).toBeVisible();
    await agentsItem.click();
    await page.waitForTimeout(3000);
    
    // Should be on agents page
    await expect(page).toHaveURL(/\/agents/i);
    
    // Should show Agent List heading
    const agentsHeading = page.locator('h2:has-text("Agent List")').first();
    await expect(agentsHeading).toBeVisible();
  });
});
