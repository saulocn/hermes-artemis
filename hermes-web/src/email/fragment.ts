/**
 * Validates and converts email body content.
 *
 * hasVisibleContent checks whether a fragment contains meaningful content
 * for the purpose of deciding whether to enable send.
 *
 * plainTextToFragment converts plain text from the editor's text mode
 * into HTML fragments, lossless in the direction that matters:
 * operator can edit as text and switch to HTML without losing structure.
 */

/**
 * Checks if a fragment has visible, meaningful content.
 *
 * Empty is: '', '<p></p>', '<p><br></p>', '<p>&nbsp;</p>', whitespace-only.
 * Visible is: any text content, or void-but-visible elements (hr, blockquote with text).
 */
export function hasVisibleContent(fragment: string): boolean {
  if (!fragment || !fragment.trim()) {
    return false;
  }

  try {
    const parser = new DOMParser();
    const doc = parser.parseFromString(fragment, 'text/html');
    const body = doc.body;

    // Check if there is any text content beyond whitespace
    const textContent = body.textContent || '';
    if (textContent.trim()) {
      return true;
    }

    // Check for void-but-visible elements (hr, blockquote without text still matters in structure)
    // hr is always visible as a line
    const hasVisibleElements = Array.from(body.querySelectorAll('hr, blockquote')).length > 0;
    if (hasVisibleElements) {
      return true;
    }

    return false;
  } catch {
    // If parsing fails, assume not visible
    return false;
  }
}

/**
 * Converts plain text to an HTML fragment.
 *
 * - Escapes text to prevent injection
 * - Splits on blank lines (two+ consecutive newlines) into <p> elements
 * - Single newlines within paragraphs become <br>
 * - Lossless: operator can switch from text to HTML without data loss
 */
export function plainTextToFragment(text: string): string {
  if (!text) {
    return '';
  }

  // Escape HTML special characters
  const escaped = text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');

  // Split on blank lines (2+ consecutive newlines), preserving the line structure
  const paragraphs = escaped.split(/\n\s*\n+/);

  // For each paragraph, replace single newlines with <br>, filtering out empty ones
  const htmlParagraphs = paragraphs
    .filter((para) => para.trim())
    .map((para) => {
      const withBr = para.replace(/\n/g, '<br>');
      return `<p>${withBr}</p>`;
    })
    .join('');

  return htmlParagraphs;
}
