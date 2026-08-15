import { useMemo, useState, type FormEvent } from 'react';
import { ApiError, createMessage } from '../api/client';

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
  const [text, setText] = useState('');
  const [contentType, setContentType] = useState('text/plain');
  const [recipientsRaw, setRecipientsRaw] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [successId, setSuccessId] = useState<number | null>(null);
  const [touched, setTouched] = useState(false);

  const recipients = useMemo(() => parseRecipients(recipientsRaw), [recipientsRaw]);
  const invalidRecipients = useMemo(
    () => recipients.filter((r) => !EMAIL_RE.test(r)),
    [recipients],
  );

  const titleError = touched && title.trim().length === 0 ? 'Informe um título.' : null;
  const textError = touched && text.trim().length === 0 ? 'Informe o texto da mensagem.' : null;
  const recipientsError =
    touched && recipients.length === 0
      ? 'Informe ao menos um destinatário.'
      : touched && invalidRecipients.length > 0
        ? `Endereços inválidos: ${invalidRecipients.join(', ')}`
        : null;

  const isValid =
    title.trim().length > 0 &&
    text.trim().length > 0 &&
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
      const response = await createMessage({
        title: title.trim(),
        text,
        contentType,
        recipients,
      });
      setSuccessId(response.id);
      setTitle('');
      setText('');
      setRecipientsRaw('');
      setTouched(false);
    } catch (err) {
      setError(describeError(err));
    } finally {
      setSubmitting(false);
    }
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
              onChange={(e) => setContentType(e.target.value)}
            >
              <option value="text/plain">text/plain</option>
              <option value="text/html">text/html</option>
            </select>
          </div>
        </div>

        <div className="field">
          <label htmlFor="text">Texto</label>
          <textarea id="text" value={text} onChange={(e) => setText(e.target.value)} />
          {textError && <div className="field-error">{textError}</div>}
        </div>

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
