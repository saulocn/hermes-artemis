/// <reference types="vitest/config" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    // Only the component tests. Without this vitest also collects e2e/*.spec.ts, which import
    // @playwright/test and fail to load — four failing files and a non-zero exit even though
    // every actual test passed. Playwright runs those itself via `npm run test:e2e`.
    include: ['src/**/*.{test,spec}.{ts,tsx}'],
    exclude: ['node_modules/**', 'dist/**', 'e2e/**'],
    coverage: {
      provider: 'v8',
      reporters: ['text', 'html'],
      exclude: ['src/test/**', 'src/main.tsx', 'src/components/RichTextEditor.tsx'], // RichTextEditor uses ProseMirror which requires real layout, covered by Playwright instead
      thresholds: {
        lines: 80,
        functions: 80,
        branches: 80,
        statements: 80,
      },
    },
  },
});
