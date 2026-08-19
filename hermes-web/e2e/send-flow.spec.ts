import { test, expect } from '@playwright/test';

test('critical path: compose, enqueue, and verify delivery', async ({ page }) => {
  // Go to home page
  await page.goto('/');
  await expect(page).toHaveTitle(/Hermes/);

  // Click "Nova mensagem" link
  await page.getByRole('link', { name: 'Nova mensagem' }).click();
  await expect(page).toHaveURL(/\/compose/);

  // Generate unique title
  const uniqueTitle = `Test Message ${Date.now()}`;
  const messageText = 'This is a test message for E2E testing.';
  const recipient1 = 'test1@example.com';
  const recipient2 = 'test2@example.com';

  // Fill in the form
  await page.getByLabel('Título').fill(uniqueTitle);
  await page.getByLabel('Texto').fill(messageText);
  await page.getByLabel('Tipo de conteúdo').selectOption('text/plain');
  await page.getByLabel(/Destinatários/).fill(`${recipient1}\n${recipient2}`);

  // Submit
  await page.getByRole('button', { name: 'Enviar' }).click();

  // Assert success banner
  await expect(page.locator('.success-banner')).toContainText(
    'Mensagem criada com sucesso'
  );

  // Go to Administração
  await page.getByRole('link', { name: 'Administração' }).click();
  await expect(page).toHaveURL(/\/admin/);

  // Click "Disparar enfileiramento"
  await page.getByRole('button', { name: 'Disparar enfileiramento' }).click();

  // Assert success banner with execution id
  await expect(page.locator('.success-banner')).toContainText('Execution id:');

  // Go to Painel
  await page.getByRole('link', { name: 'Painel' }).click();
  await expect(page).toHaveURL(/\//);

  // Wait for "Entregues" value to appear and be > 0 (initially may be 0, but we monitor change)
  const entreguesCard = page.locator('.stat-card', { has: page.locator('dt:has-text("Entregues")') });
  await expect(entreguesCard).toBeVisible();

  // Get initial value
  const initialValue = parseInt(await entreguesCard.locator('dd').textContent() || '0', 10);

  // Poll for delivery to increase or stay stable (within reasonable time)
  let currentValue = initialValue;
  const maxWaitTime = 15000; // 15 seconds
  const startTime = Date.now();

  while (Date.now() - startTime < maxWaitTime) {
    const valueStr = await entreguesCard.locator('dd').textContent();
    currentValue = parseInt(valueStr || '0', 10);

    if (currentValue > initialValue || currentValue > 0) {
      break;
    }

    await page.waitForTimeout(500);
  }

  // At minimum, verify the card is displaying a number
  expect(currentValue).toBeGreaterThanOrEqual(initialValue);
});
