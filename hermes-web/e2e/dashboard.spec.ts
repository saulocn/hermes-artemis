import { test, expect } from '@playwright/test';

test('dashboard: stat cards and broker info', async ({ page }) => {
  await page.goto('/');

  // Verify page title
  // By role and name: the Dashboard renders three <h2> (Painel, Broker de mensagens, Vazão…),
  // so a bare locator('h2') trips Playwright's strict mode.
  await expect(page.getByRole('heading', { name: 'Painel' })).toBeVisible();

  // Check four stat cards are present with numeric values
  const statCards = page.locator('.stat-card');
  await expect(statCards).toHaveCount(4);

  const cardLabels = ['Pendentes', 'Em trânsito', 'Entregues', 'Total de mensagens'];
  for (const label of cardLabels) {
    const card = page.locator('.stat-card', { has: page.locator(`dt:has-text("${label}")`) });
    await expect(card).toBeVisible();
    const value = await card.locator('dd').textContent();
    expect(value).toBeTruthy();
    expect(/^\d+$/.test(value || '')).toBeTruthy();
  }

  // Check broker card
  const brokerType = process.env.BROKER_KIND || 'artemis';
  await expect(page.locator('text=Tipo:')).toBeVisible();
  await expect(
    page.locator('p:has-text("Tipo:")')
  ).toContainText(brokerType);

  // Check queue and DLQ depth are present
  await expect(page.locator('text=Profundidade da fila:')).toBeVisible();
  await expect(page.locator('text=Profundidade da DLQ:')).toBeVisible();

  // Verify queue and DLQ depth values are displayed (may be dashes or numbers)
  const queueDepthText = await page.locator('p:has-text("Profundidade da fila:")').textContent();
  const dlqDepthText = await page.locator('p:has-text("Profundidade da DLQ:")').textContent();
  expect(queueDepthText).toBeTruthy();
  expect(dlqDepthText).toBeTruthy();
});

test('dashboard: auto-refresh triggers requests', async ({ page }) => {
  await page.goto('/');

  // Set refresh interval to 2s
  await page.locator('select#refresh-interval').selectOption('2000');

  // Monitor network requests
  const requestPromise = page.waitForRequest((request) => {
    return (
      request.url().includes('/api/admin/stats') &&
      request.method() === 'GET'
    );
  });

  // Wait for the first refresh request (should happen within 2.5s of setting the interval)
  const request = await Promise.race([
    requestPromise,
    new Promise((_, reject) =>
      setTimeout(() => reject(new Error('No refresh request within timeout')), 3000)
    ),
  ]);

  expect(request).toBeTruthy();
});
