import { describe, expect, it } from 'vitest';
import { hasVisibleContent, plainTextToFragment } from './fragment';

describe('email/fragment', () => {
  describe('hasVisibleContent', () => {
    it('returns false for empty string', () => {
      expect(hasVisibleContent('')).toBe(false);
    });

    it('returns false for whitespace-only string', () => {
      expect(hasVisibleContent('   \n  \t  ')).toBe(false);
    });

    it('returns false for empty paragraph', () => {
      expect(hasVisibleContent('<p></p>')).toBe(false);
    });

    it('returns false for paragraph with only <br>', () => {
      expect(hasVisibleContent('<p><br></p>')).toBe(false);
    });

    it('returns false for paragraph with only non-breaking space', () => {
      expect(hasVisibleContent('<p>&nbsp;</p>')).toBe(false);
    });

    it('returns false for paragraph with only whitespace', () => {
      expect(hasVisibleContent('<p>   </p>')).toBe(false);
    });

    it('returns false for multiple empty paragraphs', () => {
      expect(hasVisibleContent('<p></p><p></p><p><br></p>')).toBe(false);
    });

    it('returns true for paragraph with text', () => {
      expect(hasVisibleContent('<p>a</p>')).toBe(true);
    });

    it('returns true for paragraph with meaningful text', () => {
      expect(hasVisibleContent('<p>Hello world</p>')).toBe(true);
    });

    it('returns true for text with leading/trailing whitespace', () => {
      expect(hasVisibleContent('<p>  hello  </p>')).toBe(true);
    });

    it('returns true for hr element', () => {
      expect(hasVisibleContent('<hr>')).toBe(true);
    });

    it('returns true for hr with surrounding empty elements', () => {
      expect(hasVisibleContent('<p></p><hr><p></p>')).toBe(true);
    });

    it('returns true for blockquote with text', () => {
      expect(hasVisibleContent('<blockquote>quote text</blockquote>')).toBe(true);
    });

    it('returns true for mixed content with hr and text', () => {
      expect(hasVisibleContent('<p>text</p><hr>')).toBe(true);
    });

    it('returns true for nested text in strong', () => {
      expect(hasVisibleContent('<p><strong>bold</strong></p>')).toBe(true);
    });

    it('handles malformed HTML gracefully', () => {
      expect(hasVisibleContent('<p unclosed')).toBe(false);
    });
  });

  describe('plainTextToFragment', () => {
    it('returns empty string for empty input', () => {
      expect(plainTextToFragment('')).toBe('');
    });

    it('wraps single line in paragraph', () => {
      expect(plainTextToFragment('hello')).toBe('<p>hello</p>');
    });

    it('escapes ampersand in text', () => {
      expect(plainTextToFragment('a & b')).toBe('<p>a &amp; b</p>');
    });

    it('escapes less-than in text', () => {
      expect(plainTextToFragment('a < b')).toBe('<p>a &lt; b</p>');
    });

    it('escapes greater-than in text', () => {
      expect(plainTextToFragment('a > b')).toBe('<p>a &gt; b</p>');
    });

    it('escapes quotes in text', () => {
      expect(plainTextToFragment('a "quote" b')).toBe('<p>a &quot;quote&quot; b</p>');
    });

    it('converts single newline to br', () => {
      expect(plainTextToFragment('line1\nline2')).toBe('<p>line1<br>line2</p>');
    });

    it('converts multiple single newlines to multiple brs', () => {
      expect(plainTextToFragment('a\nb\nc')).toBe('<p>a<br>b<br>c</p>');
    });

    it('splits on blank line (two newlines) into separate paragraphs', () => {
      expect(plainTextToFragment('para1\n\npara2')).toBe('<p>para1</p><p>para2</p>');
    });

    it('splits on blank line with extra whitespace', () => {
      expect(plainTextToFragment('para1\n  \npara2')).toBe('<p>para1</p><p>para2</p>');
    });

    it('handles multiple blank lines as paragraph separator', () => {
      expect(plainTextToFragment('para1\n\n\npara2')).toBe('<p>para1</p><p>para2</p>');
    });

    it('creates three paragraphs from two blank lines', () => {
      expect(plainTextToFragment('para1\n\npara2\n\npara3')).toBe(
        '<p>para1</p><p>para2</p><p>para3</p>'
      );
    });

    it('combines single newlines within paragraphs and blank lines between them', () => {
      expect(plainTextToFragment('line1\nline2\n\nline3\nline4')).toBe(
        '<p>line1<br>line2</p><p>line3<br>line4</p>'
      );
    });

    it('escapes special characters within converted content', () => {
      expect(plainTextToFragment('a & b\nc & d')).toBe('<p>a &amp; b<br>c &amp; d</p>');
    });

    it('handles trailing whitespace after paragraph', () => {
      expect(plainTextToFragment('para1\n\n')).toBe('<p>para1</p>');
    });

    it('handles leading whitespace before first paragraph', () => {
      expect(plainTextToFragment('  para1')).toBe('<p>  para1</p>');
    });
  });
});
