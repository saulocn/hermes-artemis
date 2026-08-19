import { lazy, Suspense, useMemo, useState, useEffect, type FormEvent } from 'react';
import { ApiError, createMessage } from '../api/client';
import { buildEmailDocument } from '../email/emailDocument';
import { hasVisibleContent, plainTextToFragment } from '../email/fragment';
import { EmailPreview } from '../components/EmailPreview';
import { downloadTextFile, emailFileName } from '../browser/download';

const RichTextEditor = lazy(() => import('../components/RichTextEditor'));

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function parseRecipients(raw: string): string[] {
  return raw
    .split(/[\n,]/)
    .map((entry) => entry.trim())
    .filter((entry) => entry.length > 0);
}

function describeError(err: unknown): string {
  if (err instanceof ApiError) return err.message;
  if (err instanceof Error) return err.message;
  return 'Erro desconhecido ao enviar a mensagem.';
}

export default function Compose() {
  const [title, setTitle] = useState('');
  const [plainText, setPlainText] = useState('');
  const [htmlFragment, setHtmlFragment] = useState('');
  const [contentType, setContentType] = useState('text/plain');
  const [recipientsRaw, setRecipientsRaw] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [successId, setSuccessId] = useState<number | null>(null);
  const [touched, setTouched] = useState(false);
  const [previewHtml, setPreviewHtml] = useState('');
  const [copyNotice, setCopyNotice] = useState<string | null>(null);

  const recipients = useMemo(() => parseRecipients(recipientsRaw), [recipientsRaw]);
  const invalidRecipients = useMemo(
    () => recipients.filter((r) => !EMAIL_RE.test(r)),
    [recipients],
  );

  // Debounce preview rendering for HTML mode
  useEffect(() => {
    if (contentType !== 'text/html') return;

    const timer = setTimeout(() => {
      if (htmlFragment) {
        const doc = buildEmailDocument({ title, fragment: htmlFragment });
        setPreviewHtml(doc);
      } else {
        setPreviewHtml('');
      }
    }, 200);

    return () => clearTimeout(timer);
  }, [title, htmlFragment, contentType]);

  // Handle contentType changes
  const handleContentTypeChange = (newType: string) => {
    if (newType === 'text/html' && htmlFragment === '' && plainText !== '') {
      // Seed HTML from plain text
      setHtmlFragment(plainTextToFragment(plainText));
    }
    setContentType(newType);
  };

  const titleError = touched && title.trim().length === 0 ? 'Informe um título.' : null;
  const textError =
    touched &&
    (contentType === 'text/html'
      ? !hasVisibleContent(htmlFragment)
      : plainText.trim().length === 0)
      ? 'Informe o texto da mensagem.'
      : null;
  const recipientsError =
    touched && recipients.length === 0
      ? 'Informe ao menos um destinatário.'
      : touched && invalidRecipients.length > 0
        ? `Endereços inválidos: ${invalidRecipients.join(', ')}`
        : null;

  const isValid =
    title.trim().length > 0 &&
    (contentType === 'text/html' ? hasVisibleContent(htmlFragment) : plainText.trim().length > 0) &&
    recipients.length > 0 &&
    invalidRecipients.length === 0;

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setTouched(true);
    setError(null);
    setSuccessId(null);

    if (!isValid) return;

    setSubmitting(true);
    try {
      const messageText =
        contentType === 'text/html'
          ? buildEmailDocument({ title, fragment: htmlFragment })
          : plainText;

      const response = await createMessage({
        title: title.trim(),
        text: messageText,
        contentType,
        recipients,
      });
      setSuccessId(response.id);
      setTitle('');
      setPlainText('');
      setHtmlFragment('');
      setRecipientsRaw('');
      setTouched(false);
    } catch (err) {
      setError(describeError(err));
    } finally {
      setSubmitting(false);
    }
  }

  // Its own state, not `error`. Writing a success message into the error banner paints it red
  // and — worse — the restore-after-2s captured the old `error` in a closure, so a real failure
  // arriving during those two seconds was silently erased.
  function handleCopyHtml() {
    const doc = buildEmailDocument({ title, fragment: htmlFragment });
    navigator.clipboard
      .writeText(doc)
      .then(() => setCopyNotice('HTML copiado para a área de transferência.'))
      // writeText rejects when the document is not focused or permission is denied. Silence
      // there would leave the operator believing they had copied something.
      .catch(() => setCopyNotice('Não foi possível copiar. Use "Baixar .html".'));
  }

  function handleDownloadHtml() {
    const doc = buildEmailDocument({ title, fragment: htmlFragment });
    const filename = emailFileName(title, new Date());
    downloadTextFile(filename, 'text/html', doc);
  }

  return (
    <div>
      <h2 className="page-title">Nova mensagem</h2>

      {error && <div className="error-banner">{error}</div>}
      {successId !== null && (
        <div className="success-banner">Mensagem criada com sucesso. Id: {successId}</div>
      )}

      <form className="card" aria-label="Nova mensagem" onSubmit={handleSubmit} noValidate>
        <div className="field">
          <label htmlFor="title">Título</label>
          <input
            id="title"
            type="text"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            style={{ width: '100%' }}
          />
          {titleError && <div className="field-error">{titleError}</div>}
        </div>

        <div className="form-row">
          <div className="field">
            <label htmlFor="contentType">Tipo de conteúdo</label>
            <select
              id="contentType"
              value={contentType}
              onChange={(e) => handleContentTypeChange(e.target.value)}
            >
              <option value="text/plain">text/plain</option>
              <option value="text/html">text/html</option>
            </select>
          </div>
        </div>

        {contentType === 'text/plain' ? (
          <div className="field">
            <label htmlFor="text">Texto</label>
            <textarea
              id="text"
              value={plainText}
              onChange={(e) => setPlainText(e.target.value)}
            />
            {textError && <div className="field-error">{textError}</div>}
            {htmlFragment !== '' && (
              <p className="hint">
                O conteúdo HTML foi mantido e reaparece ao voltar para text/html.
              </p>
            )}
          </div>
        ) : (
          <>
            <div className="editor-split">
              <div className="field" style={{ height: '100%' }}>
                <label htmlFor="text">Texto</label>
                <Suspense fallback={<div>Carregando editor...</div>}>
                  <RichTextEditor
                    value={htmlFragment}
                    onChange={setHtmlFragment}
                    ariaLabel="Texto"
                    disabled={false}
                  />
                </Suspense>
                {textError && <div className="field-error">{textError}</div>}
              </div>
              <div style={{ height: '100%', overflow: 'auto' }}>
                <EmailPreview html={previewHtml} />
              </div>
            </div>
            <div className="form-row">
              <button type="button" className="secondary" onClick={handleCopyHtml}>
                Copiar HTML
              </button>
              <button type="button" className="secondary" onClick={handleDownloadHtml}>
                Baixar .html
              </button>
            </div>
            {copyNotice && (
              <p className="hint" role="status">
                {copyNotice}
              </p>
            )}
          </>
        )}

        <div className="field">
          <label htmlFor="recipients">
            Destinatários (um e-mail por linha, ou separados por vírgula)
          </label>
          <textarea
            id="recipients"
            value={recipientsRaw}
            onChange={(e) => setRecipientsRaw(e.target.value)}
            placeholder={'ana@example.com\njoao@example.com'}
          />
          {recipientsError && <div className="field-error">{recipientsError}</div>}
          <p className="hint">
            {recipients.length} destinatário(s) será(ão) enviado(s).
          </p>
        </div>

        <button type="submit" disabled={submitting}>
          {submitting ? 'Enviando…' : 'Enviar'}
        </button>
      </form>
    </div>
  );
}
