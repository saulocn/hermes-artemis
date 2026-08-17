/**
 * Wraps sanitized and styled email content into a complete XHTML document.
 *
 * The template targets Outlook's Word rendering engine and Gmail with:
 * - XHTML 1.0 Transitional doctype and VML namespaces
 * - Outlook-safe table layout with both attribute and style-based width (600px)
 * - Consistent typography and colors across clients
 *
 * escapeHtml is load-bearing: title is free operator input landing in <title>.
 * A title containing </title><script> must not escape the element. Always escape
 * & first to avoid double-encoding.
 */

// Email template color and layout constants
const BACKGROUND_COLOR = '#f4f4f5';
const CONTENT_BACKGROUND = '#ffffff';
const CONTENT_COLOR = '#1c1f26';
const CONTENT_PADDING = '24px';
const CONTENT_FONT_SIZE = '16px';
const CONTENT_LINE_HEIGHT = '24px';
const CONTENT_FONT_FAMILY = 'Arial, Helvetica, sans-serif';
const MAX_WIDTH = 600;

/**
 * Escapes HTML special characters to prevent injection.
 * IMPORTANT: This escapes the title text before it enters <title>,
 * preventing </title><script> injection. It does NOT protect the body,
 * which is the sanitizer's responsibility.
 *
 * Escape & first or you will double-encode.
 */
export function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

/**
 * Wraps bodyHtml in a complete email template for Outlook and Gmail.
 * Produces XHTML 1.0 Transitional with table-based layout and inline styles.
 */
export function wrapInEmailTemplate(bodyHtml: string, opts: { title: string }): string {
  const escapedTitle = escapeHtml(opts.title);

  return `<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:v="urn:schemas-microsoft-com:vml" xmlns:o="urn:schemas-microsoft-com:office:office">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <meta http-equiv="X-UA-Compatible" content="IE=edge">
  <title>${escapedTitle}</title>
  <!--[if mso]>
  <xml>
    <o:OfficeDocumentSettings>
      <o:PixelsPerInch>96</o:PixelsPerInch>
    </o:OfficeDocumentSettings>
  </xml>
  <![endif]-->
  <style type="text/css">
    body {
      margin: 0;
      padding: 0;
      width: 100%;
      background-color: ${BACKGROUND_COLOR};
      -webkit-text-size-adjust: 100%;
    }
  </style>
</head>
<body style="margin:0;padding:0;width:100%;background:${BACKGROUND_COLOR};-webkit-text-size-adjust:100%">
  <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="width:100%">
    <tr>
      <td align="center" style="padding:0">
        <table role="presentation" width="${MAX_WIDTH}" cellpadding="0" cellspacing="0" border="0" style="width:${MAX_WIDTH}px;max-width:${MAX_WIDTH}px">
          <tr>
            <td style="padding:${CONTENT_PADDING};font-family:${CONTENT_FONT_FAMILY};font-size:${CONTENT_FONT_SIZE};line-height:${CONTENT_LINE_HEIGHT};color:${CONTENT_COLOR};background:${CONTENT_BACKGROUND}">
              ${bodyHtml}
            </td>
          </tr>
        </table>
      </td>
    </tr>
  </table>
</body>
</html>`;
}
