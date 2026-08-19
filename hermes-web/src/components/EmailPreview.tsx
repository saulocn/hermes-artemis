/**
 * Sandboxed iframe for previewing email content.
 *
 * Uses sandbox="" (all restrictions on, no exceptions) and srcdoc for security:
 * 1. Two independent security layers: the iframe's sandbox and the sanitiser both fail independently.
 *    dangerouslySetInnerHTML would make the sanitiser the only layer between hostile pasted content
 *    and script execution inside the operator's authenticated browser origin.
 * 2. CSS isolation: the email template sets body{margin:0;background:#f4f4f5}, which
 *    dangerouslySetInnerHTML would drop, making the preview a lie. The page's global
 *    table{width:100%;border-collapse:collapse} rules would bleed into table-layout emails
 *    and make them render wrong. This iframe isolates both directions.
 * 3. It is the only way to render a whole document (html/body/head).
 */
export function EmailPreview(props: { html: string }): JSX.Element {
  return (
    <iframe
      title="Pré-visualização do e-mail"
      sandbox=""
      srcDoc={props.html}
      className="email-preview"
    />
  );
}
