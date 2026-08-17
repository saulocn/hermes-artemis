import { describe, it, expect } from 'vitest';
import { sanitizeFragment } from './sanitize';

describe('sanitize', () => {
  it('removes <script> tags', () => {
    const result = sanitizeFragment('<p>Hello</p><script>alert(1)</script>');
    expect(result).not.toContain('<script>');
    expect(result).toContain('<p>Hello</p>');
  });

  it('removes onclick and onerror attributes', () => {
    const result = sanitizeFragment('<p onclick="alert(1)">Click me</p>');
    expect(result).not.toContain('onclick');
    expect(result).toContain('<p>Click me</p>');
  });

  it('removes <iframe>, <object>, and <form> tags', () => {
    const result = sanitizeFragment(
      '<p>Text</p><iframe src="x"></iframe><object data="y"></object><form></form>'
    );
    expect(result).not.toContain('<iframe');
    expect(result).not.toContain('<object');
    expect(result).not.toContain('<form');
    expect(result).toContain('<p>Text</p>');
  });

  it('neutralizes javascript: URLs including mixed-case variants', () => {
    const result1 = sanitizeFragment('<a href="javascript:alert(1)">Click</a>');
    expect(result1).not.toContain('javascript:');

    const result2 = sanitizeFragment('<a href="jAvAsCrIpT:alert(1)">Click</a>');
    expect(result2).not.toContain('jAvAsCrIpT:');
  });

  it('preserves https: and mailto: links', () => {
    const result1 = sanitizeFragment('<a href="https://example.com">Link</a>');
    expect(result1).toContain('https://example.com');

    const result2 = sanitizeFragment('<a href="mailto:user@example.com">Email</a>');
    expect(result2).toContain('mailto:user@example.com');
  });

  it('forces rel="noopener noreferrer" and target="_blank" on every <a>', () => {
    const result = sanitizeFragment('<a href="https://example.com">Link</a>');
    expect(result).toContain('target="_blank"');
    expect(result).toContain('rel="noopener noreferrer"');
  });

  it('removes <img> with onerror handlers completely', () => {
    const result = sanitizeFragment('<p>Text</p><img src="x" onerror="alert(1)" /><p>After</p>');
    expect(result).not.toContain('<img');
    expect(result).not.toContain('onerror');
  });

  it('preserves allowed tags: h1, p, strong, ul, li, a', () => {
    const html = '<h1>Title</h1><p>Text with <strong>bold</strong></p><ul><li>Item</li></ul><a href="#">Link</a>';
    const result = sanitizeFragment(html);
    expect(result).toContain('<h1>');
    expect(result).toContain('<p>');
    expect(result).toContain('<strong>');
    expect(result).toContain('<ul>');
    expect(result).toContain('<li>');
    expect(result).toContain('<a');
  });

  it('strips style attribute from authored content', () => {
    const html = '<p style="color: red; font-size: 20px;">Styled text</p>';
    const result = sanitizeFragment(html);
    expect(result).not.toContain('style=');
    expect(result).toContain('<p>Styled text</p>');
  });
});
