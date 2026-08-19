import { describe, expect, it } from 'vitest';
import { buildEmailDocument } from './emailDocument';

describe('email/emailDocument', () => {
  it('builds complete document with title and fragment', () => {
    const doc = buildEmailDocument({ title: 'Test Email', fragment: '<p>Hello world</p>' });

    // Must be valid XHTML document
    expect(doc).toContain('<!DOCTYPE');
    expect(doc).toContain('</html>');

    // Must have the title
    expect(doc).toContain('<title>Test Email</title>');

    // Must have the fragment content
    expect(doc).toContain('Hello world');

    // Must have the 600px layout
    expect(doc).toContain('width="600"');
    expect(doc).toContain('600px');
  });

  it('sanitizes fragment by removing script tags', () => {
    const doc = buildEmailDocument({
      title: 'Test',
      fragment: '<p>safe</p><script>alert(1)</script>',
    });

    expect(doc).not.toContain('<script>');
    expect(doc).not.toContain('alert');
    expect(doc).toContain('safe');
  });

  it('sanitizes fragment by removing onclick attributes', () => {
    const doc = buildEmailDocument({
      title: 'Test',
      fragment: '<p onclick="x()">oi</p>',
    });

    expect(doc).not.toContain('onclick');
    expect(doc).toContain('oi');
  });

  it('removes both script tags and onclick from hostile fragment', () => {
    const doc = buildEmailDocument({
      title: 'Test',
      fragment: '<p onclick="x()">oi</p><script>a()</script>',
    });

    // Sanitization removes both
    expect(doc).not.toContain('onclick');
    expect(doc).not.toContain('<script>');
    expect(doc).not.toContain('a()');

    // Safe content remains
    expect(doc).toContain('oi');

    // Template structure is present
    expect(doc).toContain('<!DOCTYPE');
    expect(doc).toContain('width="600"');
  });

  it('applies inline styles to elements', () => {
    const doc = buildEmailDocument({
      title: 'Test',
      fragment: '<p>Text</p>',
    });

    // Paragraph should have inline styles from TAG_STYLES
    expect(doc).toContain('style=');
    expect(doc).toContain('margin');
  });

  it('escapes title from XSS injection', () => {
    const doc = buildEmailDocument({
      title: '</title><script>alert(1)</script>',
      fragment: '<p>content</p>',
    });

    // Title should be escaped, not create actual script
    expect(doc).not.toContain('<script>');
    expect(doc).toContain('&lt;/title&gt;');
  });

  it('handles empty fragment with valid document', () => {
    const doc = buildEmailDocument({ title: 'Test', fragment: '' });

    expect(doc).toContain('<!DOCTYPE');
    expect(doc).toContain('</html>');
    expect(doc).toContain('width="600"');
  });

  it('preserves safe HTML structure (headings, lists, links)', () => {
    const doc = buildEmailDocument({
      title: 'Newsletter',
      fragment: '<h1>Welcome</h1><p>Check this <a href="https://example.com">link</a></p><ul><li>Item 1</li></ul>',
    });

    expect(doc).toContain('Welcome');
    expect(doc).toContain('link');
    expect(doc).toContain('Item 1');
    expect(doc).toContain('https://example.com');
  });
});
