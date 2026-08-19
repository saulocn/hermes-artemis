import { describe, it, expect } from 'vitest';
import { inlineTagStyles } from './inlineStyles';

describe('inlineStyles', () => {
  it('applies styles from TAG_STYLES to h1', () => {
    const result = inlineTagStyles('<h1>Heading</h1>');
    expect(result).toContain('style="');
    expect(result).toContain('margin:0 0 16px');
    expect(result).toContain('font-size:24px');
  });

  it('styles both nested ul and li elements', () => {
    const result = inlineTagStyles('<ul><li>Item 1</li><li>Item 2</li></ul>');
    expect(result).toContain('ul');
    expect(result).toContain('li');
    // Check for ul styles
    expect(result).toContain('padding-left:24px');
    // Check for li styles
    expect(result).toContain('margin:0 0 8px');
  });

  it('preserves existing attributes like href', () => {
    const result = inlineTagStyles('<a href="https://example.com">Link</a>');
    expect(result).toContain('href="https://example.com"');
    expect(result).toContain('style=');
  });

  it('leaves unknown tags untouched', () => {
    const html = '<article>Content</article>';
    const result = inlineTagStyles(html);
    expect(result).toContain('<article>');
    expect(result).not.toContain('style="');
  });

  it('is idempotent: applying twice produces the same output', () => {
    const html = '<h1>Title</h1><p>Paragraph</p>';
    const once = inlineTagStyles(html);
    const twice = inlineTagStyles(once);
    expect(once).toBe(twice);
  });

  it('leaves strong tag without style attribute', () => {
    const result = inlineTagStyles('<strong>Bold text</strong>');
    expect(result).toContain('<strong>');
    // strong should not have style attribute since it's not in TAG_STYLES
    expect(result).toContain('Bold text');
  });
});
