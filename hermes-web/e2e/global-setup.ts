import { chromium } from '@playwright/test';

const baseURL = process.env.E2E_BASE_URL || 'http://localhost:8090';
const healthCheckUrl = `${baseURL}/api/admin/stats`;

async function globalSetup() {
  const startTime = Date.now();
  const maxWaitMs = 60000;

  let lastError: Error | null = null;
  while (Date.now() - startTime < maxWaitMs) {
    try {
      const browser = await chromium.launch();
      const context = await browser.newContext();
      const page = await context.newPage();

      const response = await page.goto(healthCheckUrl, { waitUntil: 'commit' });
      await browser.close();

      if (response?.ok) {
        console.log(`✓ Stack is healthy at ${baseURL}`);
        return;
      }
    } catch (err) {
      lastError = err instanceof Error ? err : new Error(String(err));
    }

    await new Promise((r) => setTimeout(r, 1000));
  }

  throw new Error(
    `The stack is not running. Start it with 'make up' (Artemis) or 'make up-rabbit' (RabbitMQ) from the repo root.${lastError ? ` Last error: ${lastError.message}` : ''}`
  );
}

export default globalSetup;
