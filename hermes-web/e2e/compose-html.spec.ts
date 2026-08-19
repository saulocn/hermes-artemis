import { test, expect } from '@playwright/test';

test('compose HTML: switch to HTML mode, toolbar and preview appear', async ({
  page,
}) => {
  // Navigate to compose page
  await page.goto('/compose');
  await expect(page).toHaveURL(/\/compose/);

  // By default, text/plain is selected
  await expect(page.getByLabel('Tipo de conteúdo')).toHaveValue('text/plain');

  // Switch to text/html
  await page.getByLabel('Tipo de conteúdo').selectOption('text/html');

  // Verify toolbar buttons are visible
  await expect(page.getByRole('button', { name: 'Negrito' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Itálico' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Sublinhado' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Título 1' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Título 2' })).toBeVisible();
  // exact: true, or this also matches "Lista numerada" and fails on strict mode.
  await expect(page.getByRole('button', { name: 'Lista', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Lista numerada' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Link' })).toBeVisible();
  await expect(
    page.getByRole('button', { name: 'Limpar formatação' })
  ).toBeVisible();

  // Verify live preview iframe is visible
  const previewFrame = page.frameLocator(
    'iframe[title="Pré-visualização do e-mail"]'
  );
  await expect(previewFrame.locator('body')).toBeVisible();

  // Verify editor is mounted (contentEditable with aria-label)
  const editor = page.getByLabel('Texto');
  await expect(editor).toBeVisible();
});

test('compose HTML: editor interaction and bold formatting', async ({
  page,
}) => {
  await page.goto('/compose');

  // Switch to HTML mode
  await page.getByLabel('Tipo de conteúdo').selectOption('text/html');

  // Access the WYSIWYG editor
  const editor = page.getByLabel('Texto');
  await expect(editor).toBeVisible();

  // Click editor and type text
  await editor.click();
  await page.keyboard.type('Olá');

  // Click bold button and type more text
  await page.getByRole('button', { name: 'Negrito' }).click();
  await page.keyboard.type(' mundo');

  // Assert bold text in preview iframe
  const previewFrame = page.frameLocator(
    'iframe[title="Pré-visualização do e-mail"]'
  );

  // Verify strong tag contains the bolded text
  const boldLocator = previewFrame.locator('strong');
  await expect(boldLocator).toContainText('mundo');

  // Verify there is content in the preview
  await expect(previewFrame.locator('body')).toContainText('Olá');
});

test('compose HTML: preview contains email template wrapper', async ({
  page,
}) => {
  await page.goto('/compose');

  // Switch to HTML mode
  await page.getByLabel('Tipo de conteúdo').selectOption('text/html');

  // Add some content
  const editor = page.getByLabel('Texto');
  await editor.click();
  await page.keyboard.type('Test content');

  // Verify preview iframe contains the email template wrapper
  const previewFrame = page.frameLocator(
    'iframe[title="Pré-visualização do e-mail"]'
  );

  // Assert the table with width="600" is present (proof of template wrapper)
  const emailTable = previewFrame.locator('table[width="600"]');
  await expect(emailTable).toBeVisible();
});

test('compose HTML: download HTML file', async ({ page }) => {
  await page.goto('/compose');

  // Switch to HTML mode
  await page.getByLabel('Tipo de conteúdo').selectOption('text/html');

  // Add content
  const editor = page.getByLabel('Texto');
  await editor.click();
  await page.keyboard.type('Download test');

  // Wait for download and verify filename
  const downloadPromise = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Baixar .html' }).click();

  const download = await downloadPromise;
  const filename = download.suggestedFilename();

  // Assert filename ends with .html
  expect(filename).toMatch(/\.html$/);
});

test('compose HTML: copy HTML confirmation', async ({ page }) => {
  await page.goto('/compose');

  // Switch to HTML mode
  await page.getByLabel('Tipo de conteúdo').selectOption('text/html');

  // Add content
  const editor = page.getByLabel('Texto');
  await editor.click();
  await page.keyboard.type('Copy test');

  // Click copy button
  await page.getByRole('button', { name: 'Copiar HTML' }).click();

  // Assert transient on-screen confirmation appears
  // We do NOT read the clipboard as it requires 'clipboard-read' permission,
  // is chromium-specific, and is flaky in headless mode. The authoritative test
  // for clipboard functionality belongs in the vitest suite.
  // Instead, verify the visual feedback (e.g., a toast, alert, or success message).
  // The exact selector depends on the UI implementation.
  const successIndicator = page.locator('[role="status"], .toast, .alert');
  await expect(successIndicator.first()).toBeVisible();
});

test('compose HTML: full send flow with history verification', async ({
  page,
}) => {
  const uniqueTitle = `HTML Test ${Date.now()}`;
  const messageText = 'Full send test with HTML content.';
  const recipient1 = 'html-test1@example.com';
  const recipient2 = 'html-test2@example.com';

  // Navigate to compose
  await page.goto('/compose');

  // Fill title
  await page.getByLabel('Título').fill(uniqueTitle);

  // Switch to HTML mode
  await page.getByLabel('Tipo de conteúdo').selectOption('text/html');

  // Fill editor with content
  const editor = page.getByLabel('Texto');
  await editor.click();
  await page.keyboard.type(messageText);

  // Fill recipients
  await page.getByLabel(/Destinatários/).fill(`${recipient1}\n${recipient2}`);

  // Submit
  await page.getByRole('button', { name: 'Enviar' }).click();

  // Verify success banner
  await expect(page.locator('.success-banner')).toContainText(
    'Mensagem criada com sucesso'
  );

  // Navigate to history
  await page.getByRole('link', { name: 'Histórico' }).click();
  await expect(page).toHaveURL(/\/history/);

  // Search for the message
  const searchInput = page.getByLabel('Buscar mensagens');
  await searchInput.fill(uniqueTitle);

  // Wait for search results to load
  await page.waitForTimeout(500);

  // Find the message in the history table
  const rows = page.locator('table tbody tr');
  let found = false;

  for (let i = 0; i < await rows.count(); i++) {
    const row = rows.nth(i);
    const titleCell = await row.locator('td').nth(1).textContent();

    if (titleCell?.includes(uniqueTitle)) {
      found = true;

      // Verify the row shows text/html content type
      // The content type should be displayed in the table (exact column depends on design)
      const rowText = await row.textContent();
      expect(rowText).toContain('text/html');

      break;
    }
  }

  expect(found).toBeTruthy();
});
