import { describe, expect, it } from 'vitest';
import { escapeHtml, wrapInEmailTemplate } from './template';

describe('email/template', () => {
  describe('escapeHtml', () => {
    it('escapes ampersand', () => {
      expect(escapeHtml('a & b')).toBe('a &amp; b');
    });

    it('escapes ampersand exactly once (not double-encoded)', () => {
      // If & is not escaped first, the < in &lt; will be escaped to &amp;lt;
      expect(escapeHtml('a & b')).not.toBe('a &amp;amp; b');
    });

    it('escapes less-than', () => {
      expect(escapeHtml('a < b')).toBe('a &lt; b');
    });

    it('escapes greater-than', () => {
      expect(escapeHtml('a > b')).toBe('a &gt; b');
    });

    it('escapes double quotes', () => {
      expect(escapeHtml('a "b" c')).toBe('a &quot;b&quot; c');
    });

    it('escapes single quotes', () => {
      expect(escapeHtml("a 'b' c")).toBe('a &#39;b&#39; c');
    });

    it('prevents title injection with </title><script>', () => {
      const escaped = escapeHtml('</title><script>alert(1)</script>');
      expect(escaped).toBe('&lt;/title&gt;&lt;script&gt;alert(1)&lt;/script&gt;');
    });

    it('handles complex injection attempts', () => {
      const escaped = escapeHtml('x" onload="x');
      expect(escaped).toContain('&quot;');
      expect(escaped).not.toContain('"');
    });
  });

  describe('wrapInEmailTemplate', () => {
    it('includes XHTML 1.0 Transitional doctype', () => {
      const html = wrapInEmailTemplate('<p>test</p>', { title: 'Test' });
      expect(html).toContain('<!DOCTYPE html PUBLIC');
      expect(html).toContain('XHTML 1.0 Transitional');
    });

    it('includes VML namespaces', () => {
      const html = wrapInEmailTemplate('<p>test</p>', { title: 'Test' });
      expect(html).toContain('xmlns:v="urn:schemas-microsoft-com:vml"');
      expect(html).toContain('xmlns:o="urn:schemas-microsoft-com:office:office"');
    });

    it('sets the title from options', () => {
      const html = wrapInEmailTemplate('<p>test</p>', { title: 'My Title' });
      expect(html).toContain('<title>My Title</title>');
    });

    it('escapes title to prevent injection', () => {
      const html = wrapInEmailTemplate('<p>test</p>', { title: '</title><script>alert(1)</script>' });
      expect(html).toContain('&lt;/title&gt;');
      expect(html).not.toContain('<script>');
      // Verify the title tag itself is properly closed
      expect(html).toContain('<title>&lt;/title&gt;&lt;script&gt;');
    });

    it('includes charset utf-8 meta tag', () => {
      const html = wrapInEmailTemplate('<p>test</p>', { title: 'Test' });
      expect(html).toContain('<meta charset="utf-8">');
    });

    it('includes viewport meta tag', () => {
      const html = wrapInEmailTemplate('<p>test</p>', { title: 'Test' });
      expect(html).toContain('viewport');
    });

    it('includes X-UA-Compatible IE=edge', () => {
      const html = wrapInEmailTemplate('<p>test</p>', { title: 'Test' });
      expect(html).toContain('X-UA-Compatible');
      expect(html).toContain('IE=edge');
    });

    it('includes MSO conditional block with PixelsPerInch', () => {
      const html = wrapInEmailTemplate('<p>test</p>', { title: 'Test' });
      expect(html).toContain('<!--[if mso]>');
      expect(html).toContain('OfficeDocumentSettings');
      expect(html).toContain('PixelsPerInch');
    });

    it('includes outer table with 100% width', () => {
      const html = wrapInEmailTemplate('<p>test</p>', { title: 'Test' });
      expect(html).toContain('role="presentation"');
      expect(html).toContain('width="100%"');
    });

    it('includes inner table with width="600" as attribute', () => {
      const html = wrapInEmailTemplate('<p>test</p>', { title: 'Test' });
      expect(html).toContain('width="600"');
    });

    it('includes inner table with max-width style', () => {
      const html = wrapInEmailTemplate('<p>test</p>', { title: 'Test' });
      expect(html).toMatch(/style="[^"]*max-width:600px/);
    });

    it('places fragment in content cell with proper styling', () => {
      const html = wrapInEmailTemplate('<p>My content</p>', { title: 'Test' });
      expect(html).toContain('<p>My content</p>');
      // Verify it's in a td with padding and styling
      expect(html).toMatch(/<td[^>]*padding:24px/);
    });

    it('applies content cell styles', () => {
      const html = wrapInEmailTemplate('<p>test</p>', { title: 'Test' });
      expect(html).toContain('font-family:Arial, Helvetica, sans-serif');
      expect(html).toContain('font-size:16px');
      expect(html).toContain('line-height:24px');
      expect(html).toContain('color:#1c1f26');
      expect(html).toContain('background:#ffffff');
    });

    it('applies body styles', () => {
      const html = wrapInEmailTemplate('<p>test</p>', { title: 'Test' });
      expect(html).toContain('background:#f4f4f5');
      expect(html).toContain('-webkit-text-size-adjust:100%');
    });

    it('produces valid document with empty body', () => {
      const html = wrapInEmailTemplate('', { title: 'Test' });
      expect(html).toContain('<!DOCTYPE');
      expect(html).toContain('</html>');
      expect(html).toContain('<title>Test</title>');
    });

    it('handles special characters in content without escaping them', () => {
      // The template should pass through the fragment as-is (escaping is sanitizer's job)
      const html = wrapInEmailTemplate('<p>test &amp; more</p>', { title: 'Test' });
      expect(html).toContain('<p>test &amp; more</p>');
    });
  });
});
