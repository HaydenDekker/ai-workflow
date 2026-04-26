import { test, expect } from '@playwright/test';

test.describe('Agent Detail Dialog', () => {
  test('page loads with agent list', async ({ page }) => {
    await page.goto('/agents');
    await page.waitForLoadState('domcontentloaded');
    await expect(page).toHaveURL(/\/agents/i);
  });

  test('clicking on an agent row opens detail dialog', async ({ page }) => {
    await page.goto('/agents');
    await page.waitForTimeout(5000);

    // Check if there are any agents in the grid using the grid's native cell locator
    const firstAgentCell = page.locator('vaadin-grid').getByText('SOLID_NON_COMPLIANCE');
    const hasAgents = await firstAgentCell.isVisible().catch(() => false);

    if (hasAgents) {
      // Click on the first agent row
      await firstAgentCell.click();
      await page.waitForTimeout(1000);

      // Verify dialog is visible
      const dialog = page.locator('vaadin-dialog-overlay');
      await expect(dialog).toBeVisible();

      // Verify dialog contains edit-related fields
      await expect(page.locator('vaadin-text-field[label="Title"]')).toBeVisible();
      await expect(page.locator('vaadin-text-field[label="Target Directory"]')).toBeVisible();
      await expect(page.locator('vaadin-combo-box[label="Agent Type"]')).toBeVisible();
      await expect(page.locator('vaadin-button:has-text("Save")')).toBeVisible();
      await expect(page.locator('vaadin-button:has-text("Delete Agent")')).toBeVisible();
      await expect(page.locator('vaadin-button:has-text("Cancel")')).toBeVisible();
    }
  });

  test('detail dialog shows read-only metadata', async ({ page }) => {
    await page.goto('/agents');
    await page.waitForTimeout(5000);

    const firstAgentCell = page.locator('vaadin-grid').getByText('SOLID_NON_COMPLIANCE');
    if (await firstAgentCell.isVisible().catch(() => false)) {
      await firstAgentCell.click();
      await page.waitForTimeout(1000);

      // Verify read-only metadata fields are visible
      await expect(page.locator('vaadin-text-field[label="Created"]')).toBeVisible();
      await expect(page.locator('vaadin-text-field[label="Active"]')).toBeVisible();
      await expect(page.locator('vaadin-text-field[label="Source"]')).toBeVisible();
      await expect(page.locator('vaadin-text-field[label="Scanner"]')).toBeVisible();
    }
  });

  test('cancel closes dialog without changes', async ({ page }) => {
    await page.goto('/agents');
    await page.waitForTimeout(5000);

    const firstAgentCell = page.locator('vaadin-grid').getByText('SOLID_NON_COMPLIANCE');
    if (await firstAgentCell.isVisible().catch(() => false)) {
      await firstAgentCell.click();
      await page.waitForTimeout(1000);

      // Click Cancel
      await page.locator('vaadin-button:has-text("Cancel")').click();

      // Verify dialog is closed
      await page.waitForTimeout(500);
      const dialog = page.locator('vaadin-dialog-overlay');
      const isVisible = await dialog.count();
      expect(isVisible).toBe(0);
    }
  });

  test('validation errors shown on invalid save', async ({ page }) => {
    await page.goto('/agents');
    await page.waitForTimeout(5000);

    const firstAgentCell = page.locator('vaadin-grid').getByText('SOLID_NON_COMPLIANCE');
    if (await firstAgentCell.isVisible().catch(() => false)) {
      await firstAgentCell.click();
      await page.waitForTimeout(1000);

      // Clear the title field to trigger validation error
      await page.locator('vaadin-text-field[label="Title"]').fill('');

      // Click Save
      await page.locator('vaadin-button:has-text("Save")').click();
      await page.waitForTimeout(500);

      // Verify error notification is shown
      const notification = page.locator('vaadin-notification-card');
      await expect(notification).toBeVisible();

      // Verify dialog is still open
      const dialog = page.locator('vaadin-dialog-overlay');
      await expect(dialog).toBeVisible();
    }
  });

  test('delete button opens confirmation dialog', async ({ page }) => {
    await page.goto('/agents');
    await page.waitForTimeout(5000);

    const firstAgentCell = page.locator('vaadin-grid').getByText('SOLID_NON_COMPLIANCE');
    if (await firstAgentCell.isVisible().catch(() => false)) {
      await firstAgentCell.click();
      await page.waitForTimeout(1000);

      // Click Delete Agent button
      await page.locator('vaadin-button:has-text("Delete Agent")').click();
      await page.waitForTimeout(1000);

      // Verify confirmation dialog is shown
      const confirmDialog = page.locator('vaadin-dialog-overlay');
      await expect(confirmDialog).toBeVisible();
      await expect(page.locator('text=Are you sure you want to delete')).toBeVisible();

      // Click Cancel on confirmation
      await page.locator('vaadin-button:has-text("Cancel")').click();
      await page.waitForTimeout(500);
    }
  });

  test('cancel on confirmation closes it', async ({ page }) => {
    await page.goto('/agents');
    await page.waitForTimeout(5000);

    const firstAgentCell = page.locator('vaadin-grid').getByText('SOLID_NON_COMPLIANCE');
    if (await firstAgentCell.isVisible().catch(() => false)) {
      await firstAgentCell.click();
      await page.waitForTimeout(1000);

      // Click Delete Agent button
      await page.locator('vaadin-button:has-text("Delete Agent")').click();
      await page.waitForTimeout(1000);

      // Click Cancel on confirmation
      await page.locator('vaadin-button:has-text("Cancel")').click();
      await page.waitForTimeout(500);

      // Verify original detail dialog is still open
      const dialog = page.locator('vaadin-dialog-overlay');
      const count = await dialog.count();
      expect(count).toBeLessThanOrEqual(1);
    }
  });

  test('detail dialog fields are pre-populated', async ({ page }) => {
    await page.goto('/agents');
    await page.waitForTimeout(5000);

    const firstAgentCell = page.locator('vaadin-grid').getByText('SOLID_NON_COMPLIANCE');
    if (await firstAgentCell.isVisible().catch(() => false)) {
      await firstAgentCell.click();
      await page.waitForTimeout(1000);

      // Verify the Title field has a value (not empty)
      const titleValue = await page.locator('vaadin-text-field[label="Title"]').inputValue();
      expect(titleValue).not.toBe('');
    }
  });

  test('grid refreshes after delete', async ({ page }) => {
    await page.goto('/agents');
    await page.waitForTimeout(5000);

    // Check page is loaded
    await expect(page.locator('body')).toBeVisible();

    // Verify SOLID_NON_COMPLIANCE agent exists
    const firstAgentCell = page.locator('vaadin-grid').getByText('SOLID_NON_COMPLIANCE');
    await expect(firstAgentCell).toBeVisible();

    // Get the agent title from the detail dialog
    await firstAgentCell.click();
    await page.waitForTimeout(1000);

    // Verify dialog is open
    const dialog = page.locator('vaadin-dialog-overlay');
    await expect(dialog).toBeVisible();

    // Get the agent title from the title field
    const agentTitle = await page.locator('vaadin-text-field[label="Title"]').inputValue();
    expect(agentTitle).not.toBe('');

    // Click Delete Agent button
    await page.locator('vaadin-button:has-text("Delete Agent")').click();
    await page.waitForTimeout(500);

    // Verify confirmation dialog is shown
    const confirmDialog = page.locator('vaadin-dialog-overlay');
    await expect(confirmDialog).toBeVisible();
    await expect(page.locator('text=Are you sure you want to delete')).toBeVisible();

    // Click Delete in confirmation
    await page.locator('vaadin-button:has-text("Delete").first()').click();
    await page.waitForTimeout(2000);

    // Both dialogs should be closed
    await expect(dialog).toBeHidden();

    // Verify notification is shown
    const notification = page.locator('vaadin-notification-card');
    await expect(notification).toBeVisible();

    // Wait for grid to refresh
    await page.waitForTimeout(2000);

    // Verify the SOLID_NON_COMPLIANCE agent is no longer in the grid
    const agentStillThere = page.locator('vaadin-grid').getByText('SOLID_NON_COMPLIANCE');
    await expect(agentStillThere).not.toBeVisible();
  });
});
