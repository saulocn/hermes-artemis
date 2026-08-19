/**
 * Generates a sanitized email filename with date suffix.
 * @example
 * emailFileName('Promoção de Verão!', new Date('2026-08-16')) => 'promocao-de-verao-2026-08-16.html'
 */
export function emailFileName(title: string, at: Date): string {
  // Strip diacritics using Unicode normalization
  const normalized = title.normalize('NFD').replace(/[̀-ͯ]/g, '');

  // Lowercase and replace non-alphanumerics with hyphens
  const slug = normalized
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, ''); // Trim leading/trailing hyphens

  // Cap slug length (reasonable default for readability)
  const cappedSlug = slug.substring(0, 100);

  // Format date as ISO YYYY-MM-DD
  const dateStr = at.toISOString().split('T')[0];

  return `${cappedSlug}-${dateStr}.html`;
}

/**
 * Downloads text content as a file by creating a blob and triggering download.
 * @param name - The filename (including extension)
 * @param mime - The MIME type (e.g., 'text/html')
 * @param content - The file content
 */
export function downloadTextFile(name: string, mime: string, content: string): void {
  const blob = new Blob([content], { type: mime });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = name;
  document.body.appendChild(anchor);
  anchor.click();
  document.body.removeChild(anchor);
  URL.revokeObjectURL(url);
}
