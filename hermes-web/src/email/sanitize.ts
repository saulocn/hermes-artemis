import DOMPurify from 'dompurify';

/**
 * Sanitizes an HTML fragment for safe email delivery.
 *
 * This function is the sole sanitizer in the email pipeline: authored HTML →
 * sanitizeFragment → inlineStyles → wrapInEmailTemplate. It runs BEFORE the inliner,
 * so the inliner never walks hostile nodes and styles it injects are never re-inspected.
 *
 * The server (Java mailer) accepts anything posted to it, so this sanitizer protects:
 * - The operator's own browser when previewing (the preview iframe renders the HTML)
 * - Recipients from junk markup and XSS vectors
 *
 * Pasted Word/Google Docs markup often arrives with enormous junk CSS. That styling
 * is deliberately lost here; the inliner is the single place where email styling is decided.
 */
export function sanitizeFragment(html: string): string {
  const config = {
    ALLOWED_TAGS: ['p', 'br', 'strong', 'b', 'em', 'i', 'u', 's', 'a', 'h1', 'h2', 'h3', 'ul', 'ol', 'li', 'blockquote', 'code', 'pre', 'hr', 'span', 'div'],
    ALLOWED_ATTR: ['href', 'target', 'rel'],
    ALLOWED_URI_REGEXP: /^(?:(?:(?:f|ht)tps?|mailto|#):|[^a-z]|[a-z+.\-]*(?:[^a-z+.\-:]|$))/i,
    FORBID_TAGS: ['script', 'style', 'iframe', 'object', 'embed', 'form', 'input', 'link', 'meta', 'base'],
    KEEP_CONTENT: true,
  };

  // Register the afterSanitizeAttributes hook to force secure attributes on <a> tags.
  // This hook applies AFTER attribute filtering so it only touches allowed attributes.
  // Registering it as a function (not inline) ensures it cannot leak between calls or double-apply.
  const enforceAnchorSecurityHook = (node: Element) => {
    if (node.tagName === 'A') {
      node.setAttribute('target', '_blank');
      node.setAttribute('rel', 'noopener noreferrer');
    }
  };

  DOMPurify.addHook('afterSanitizeAttributes', enforceAnchorSecurityHook);
  const sanitized = DOMPurify.sanitize(html, config);
  DOMPurify.removeHook('afterSanitizeAttributes');

  return sanitized;
}
