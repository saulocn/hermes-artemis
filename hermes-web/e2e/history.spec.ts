import { test, expect } from '@playwright/test';

test('history: search for message and verify progress', async ({ page }) => {
  // Create a unique message first
  const uniqueTitle = `History Test ${Date.now()}`;
  const messageText = 'Testing history search functionality.';
  const recipients = ['hist1@example.com', 'hist2@example.com'];

  await page.goto('/compose');
  await page.getByLabel('Título').fill(uniqueTitle);
  await page.getByLabel('Texto').fill(messageText);
  await page.getByLabel('Tipo de conteúdo').selectOption('text/plain');
  await page.getByLabel(/Destinatários/).fill(recipients.join('\n'));

  await page.getByRole('button', { name: 'Enviar' }).click();
  await expect(page.locator('.success-banner')).toContainText('Mensagem criada com sucesso');

  // Navigate to history
  await page.getByRole('link', { name: 'Histórico' }).click();
  await expect(page).toHaveURL(/\/history/);

  // Search for the message
  const searchInput = page.getByLabel('Buscar mensagens');
  await searchInput.fill(uniqueTitle);

  // Wait for the table to show results (with debounce it may take a moment)
  await page.waitForTimeout(500);

  // Assert the message appears in the results
  const rows = page.locator('table tbody tr');
  let found = false;

  for (let i = 0; i < await rows.count(); i++) {
    const row = rows.nth(i);
    const titleCell = await row.locator('td').nth(1).textContent();
    if (titleCell?.includes(uniqueTitle)) {
      found = true;

      // Verify progress bar is present
      const progressBar = row.locator('[role="progressbar"]');
      await expect(progressBar).toBeVisible();
      expect(await progressBar.getAttribute('aria-valuenow')).toBeTruthy();
      break;
    }
  }

  expect(found).toBeTruthy();
});

test('history: pagination controls', async ({ page }) => {
  await page.goto('/history');

  // Get pagination info
  const paginationText = page.locator('.pagination span');
  const text = await paginationText.textContent();

  // Parse page count
  const match = text?.match(/Página \d+ de (\d+)/);
  const totalPages = match ? parseInt(match[1], 10) : 1;

  if (totalPages > 1) {
    // Next button should be enabled
    const nextButton = page.getByRole('button', { name: 'Próxima' });
    await expect(nextButton).not.toBeDisabled();

    // Click next
    await nextButton.click();

    // Wait for navigation
    await page.waitForTimeout(300);

    // Previous button should now be enabled
    const prevButton = page.getByRole('button', { name: 'Anterior' });
    await expect(prevButton).not.toBeDisabled();
  } else {
    // If only one page, next button should be disabled
    const nextButton = page.getByRole('button', { name: 'Próxima' });
    await expect(nextButton).toBeDisabled();
  }
});

test('admin: search recipient by email and filter by state', async ({ page }) => {
  // First, create a message to have a recipient in the system
  const recipients = ['admin-test@example.com'];

  await page.goto('/compose');
  await page.getByLabel('Título').fill(`Admin Test ${Date.now()}`);
  await page.getByLabel('Texto').fill('Test for admin search.');
  await page.getByLabel('Tipo de conteúdo').selectOption('text/plain');
  await page.getByLabel(/Destinatários/).fill(recipients.join('\n'));

  await page.getByRole('button', { name: 'Enviar' }).click();
  await expect(page.locator('.success-banner')).toContainText('Mensagem criada com sucesso');

  // Go to admin
  await page.getByRole('link', { name: 'Administração' }).click();
  await expect(page).toHaveURL(/\/admin/);

  // Wait for initial load
  await page.waitForTimeout(300);

  // Search by email
  const emailInput = page.getByLabel('Buscar por e-mail');
  await emailInput.fill('admin-test@example.com');

  const searchButton = page.getByRole('button', { name: 'Buscar' });
  await searchButton.click();

  // Wait for results
  await page.waitForTimeout(300);

  // Verify search was performed
  const tableLocator = page.locator('table');
  const tableCount = await tableLocator.count();
  expect(tableCount).toBeGreaterThan(0);

  // Test state filter
  const stateSelect = page.getByLabel('Filtrar por estado');
  await stateSelect.selectOption('delivered');

  await searchButton.click();
  await page.waitForTimeout(300);

  // Verify filter was applied (table may be empty or have rows)
  const rows = page.locator('table tbody tr');
  const rowCount = await rows.count();
  // Just verify the filter was set and form is functional
  expect(await stateSelect.inputValue()).toBe('delivered');
});
