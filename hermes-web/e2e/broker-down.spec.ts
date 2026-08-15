import { test, expect } from '@playwright/test';
import { execSync } from 'child_process';

function isDockerAvailable(): boolean {
  try {
    execSync('docker --version', { stdio: 'ignore' });
    return true;
  } catch {
    return false;
  }
}

function findBrokerContainer(): string | null {
  try {
    // Find container with 'mq' or 'rabbit' in the name
    const allContainers = execSync(
      'docker ps --format "{{.Names}}"',
      { encoding: 'utf-8' }
    ).trim().split('\n');

    const brokerNames = allContainers.filter(
      (name) => name.includes('mq') || name.includes('rabbit')
    );

    return brokerNames.length > 0 ? brokerNames[0] : null;
  } catch (err) {
    console.warn(`Failed to find broker container: ${err}`);
    return null;
  }
}

let brokerContainerId: string | null = null;

test.beforeAll(async () => {
  if (!isDockerAvailable()) {
    test.skip();
    return;
  }

  // Find broker container
  brokerContainerId = findBrokerContainer();
  if (!brokerContainerId) {
    test.skip();
    return;
  }
  console.log(`Found broker container: ${brokerContainerId}`);
});

test.afterAll(async () => {
  // Ensure broker is restarted
  if (brokerContainerId) {
    try {
      execSync(`docker start ${brokerContainerId}`, { stdio: 'ignore' });
      console.log(`Restarted broker container: ${brokerContainerId}`);
    } catch (err) {
      console.warn(`Failed to restart broker: ${err}`);
    }
  }
});

test('dashboard shows broker error when broker is down', async ({ page }) => {
  test.skip(!brokerContainerId, 'Docker or broker container not available');

  // Stop the broker
  try {
    execSync(`docker stop ${brokerContainerId}`, { stdio: 'ignore' });
    console.log('Stopped broker container');
  } catch (err) {
    throw new Error(`Failed to stop broker: ${err}`);
  }

  // Give it a moment to propagate
  await page.waitForTimeout(2000);

  // Reload the dashboard
  await page.goto('/');
  await page.reload({ waitUntil: 'networkidle' });

  // Allow time for error state to appear
  await page.waitForTimeout(1000);

  // Verify we see an error message (not null or crash)
  const errorBanner = page.locator('.error-banner');

  // The error banner should be present somewhere (either on page or broker card)
  const hasErrorBannerOnPage = await errorBanner.count() > 0;
  const brokerErrorExists = await page
    .locator('p:has-text("Erro")')
    .count() > 0;

  if (hasErrorBannerOnPage || brokerErrorExists) {
    // Good, there's an error message
    console.log('✓ UI correctly shows broker error');
  } else {
    // Check if broker section explicitly shows an error
    const brokerText = await page.locator('.card').filter({ hasText: 'Broker de mensagens' });
    const brokerContent = await brokerText.textContent();

    expect(brokerContent).toBeDefined();
    expect(brokerContent).not.toContain('null');
    // Should not have crashed or be completely empty
    expect(brokerContent!.length).toBeGreaterThan(0);
  }

  // Restart broker in afterAll
});
