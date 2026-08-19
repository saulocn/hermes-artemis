import '@testing-library/jest-dom/vitest';
import { afterEach, vi } from 'vitest';

process.env.TZ = 'America/Sao_Paulo';

afterEach(() => {
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});
