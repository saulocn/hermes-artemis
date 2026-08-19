/**
 * Inlines email-safe styles into HTML fragments for Outlook and Gmail compatibility.
 *
 * Gmail clips <style> blocks and Outlook ignores most of them, so every style must land
 * as an inline `style` attribute. This module walks the fragment tree and applies styles
 * from TAG_STYLES to matching elements, then serializes back to HTML.
 *
 * The styles are tuned for Outlook/Gmail and must remain exactly as specified.
 */

export const TAG_STYLES: Record<string, string> = {
  h1: 'margin:0 0 16px;font-size:24px;line-height:32px;font-weight:700',
  h2: 'margin:0 0 16px;font-size:20px;line-height:28px;font-weight:700',
  h3: 'margin:0 0 16px;font-size:18px;line-height:26px;font-weight:700',
  p: 'margin:0 0 16px;font-size:16px;line-height:24px',
  ul: 'margin:0 0 16px;padding-left:24px',
  ol: 'margin:0 0 16px;padding-left:24px',
  li: 'margin:0 0 8px',
  a: 'color:#2f5fd6;text-decoration:underline',
  blockquote: 'margin:0 0 16px;padding:8px 16px;border-left:3px solid #d9dce3;color:#5b6270',
  hr: 'border:0;border-top:1px solid #d9dce3;margin:24px 0',
  code: 'font-family:monospace;background-color:#f5f5f5;padding:2px 4px;border-radius:2px',
  pre: 'font-family:monospace;background-color:#f5f5f5;padding:8px 12px;border-radius:2px;overflow-x:auto',
};

/**
 * Applies inline styles to HTML elements based on TAG_STYLES.
 *
 * - Walks the fragment tree using DOMParser
 * - Sets `style` attribute on each element whose tag is in TAG_STYLES
 * - Leaves unknown tags and formatting tags (strong, em, u, s) untouched — they render natively
 * - Returns serialized HTML
 *
 * Must be idempotent: applying twice produces the same output.
 */
export function inlineTagStyles(fragment: string): string {
  const parser = new DOMParser();
  const doc = parser.parseFromString(fragment, 'text/html');

  // Walk all elements in the document
  const walker = doc.createTreeWalker(
    doc.body,
    NodeFilter.SHOW_ELEMENT,
    null
  );

  const elementsToStyle: Element[] = [];
  let node = walker.nextNode();
  while (node) {
    elementsToStyle.push(node as Element);
    node = walker.nextNode();
  }

  // Apply styles from TAG_STYLES to matching elements
  elementsToStyle.forEach((element) => {
    const tagName = element.tagName.toLowerCase();
    if (TAG_STYLES[tagName]) {
      element.setAttribute('style', TAG_STYLES[tagName]);
    }
  });

  // Serialize back to HTML (body.innerHTML contains all styled elements)
  return doc.body.innerHTML;
}
