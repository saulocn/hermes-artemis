import { describe, expect, it } from 'vitest';
import { emailFileName } from './download';

describe('emailFileName', () => {
  it('strips diacritics and lowercases', () => {
    const result = emailFileName('Promoção de Verão!', new Date('2026-08-16'));
    expect(result).toBe('promocao-de-verao-2026-08-16.html');
  });

  it('replaces non-alphanumerics with single hyphens', () => {
    const result = emailFileName('Hello!!! World???', new Date('2026-01-01'));
    expect(result).toBe('hello-world-2026-01-01.html');
  });

  it('trims leading and trailing hyphens', () => {
    const result = emailFileName('---Test---', new Date('2026-01-01'));
    expect(result).toBe('test-2026-01-01.html');
  });

  it('handles special characters and spaces', () => {
    const result = emailFileName('Black Friday 2026 @ 50%', new Date('2026-11-30'));
    expect(result).toBe('black-friday-2026-50-2026-11-30.html');
  });

  it('includes .html extension', () => {
    const result = emailFileName('Newsletter', new Date('2026-06-15'));
    expect(result).toContain('.html');
  });

  it('formats date as ISO YYYY-MM-DD', () => {
    const result = emailFileName('Test', new Date('2026-08-16T15:30:00Z'));
    expect(result).toMatch(/2026-08-16\.html$/);
  });

  it('preserves numbers in title', () => {
    const result = emailFileName('2026 Campaign 123', new Date('2026-01-01'));
    expect(result).toBe('2026-campaign-123-2026-01-01.html');
  });
});
