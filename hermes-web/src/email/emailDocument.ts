import { sanitizeFragment } from './sanitize';
import { inlineTagStyles } from './inlineStyles';
import { wrapInEmailTemplate } from './template';

/**
 * Builds a complete email document from a title and HTML fragment.
 *
 * Pipeline: fragment → sanitize → inline styles → wrap in template
 *
 * This function is the anti-bypass point. Sanitisation cannot be skipped
 * because there is no path to the template that does not cross it.
 * The order (sanitise, then inline, then wrap) is normative:
 * - The inliner never walks hostile nodes (sanitiser removes them first)
 * - Styles the inliner injects are never re-inspected (they are applied before wrapping)
 * - The template layer is purely structural and safe
 */
export function buildEmailDocument(input: { title: string; fragment: string }): string {
  const sanitized = sanitizeFragment(input.fragment);
  const styled = inlineTagStyles(sanitized);
  const document = wrapInEmailTemplate(styled, { title: input.title });
  return document;
}
