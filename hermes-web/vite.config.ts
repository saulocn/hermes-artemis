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
      // `test.exclude` above keeps Playwright specs from being *run* here, but they were still
      // counted in the coverage denominator at 0% — a runner grading itself on files it never
      // executes. It cut both ways: it diluted real gaps under src/ by averaging them against
      // dead weight, and it pushed the total to 80.11% against an 80% gate, so an honest test
      // could have gone red for a reason with nothing to do with the code under test.
      exclude: [
        'e2e/**',
        '*.config.*',
        'src/test/**',
        'src/main.tsx',
        // ProseMirror needs real layout and will not mount under jsdom; covered by Playwright.
        'src/components/RichTextEditor.tsx',
      ],
      thresholds: {
        lines: 80,
        functions: 80,
        branches: 80,
        statements: 80,
      },
    },
  },
});
