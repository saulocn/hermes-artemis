import { useEffect, useState, type FormEvent } from 'react';
import {
  ApiError,
  RECIPIENT_STATES,
  getRecipients,
  retryRecipient,
  triggerJob,
  type Recipient,
  type RecipientState,
} from '../api/client';

const PAGE_SIZE = 20;

const STATE_LABELS = Object.fromEntries(
  RECIPIENT_STATES.map((s) => [s.value, s.label]),
) as Record<RecipientState, string>;

function describeError(err: unknown): string {
  if (err instanceof ApiError) return err.message;
  if (err instanceof Error) return err.message;
  return 'Erro desconhecido.';
}

function formatDate(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return iso;
  return date.toLocaleString('pt-BR');
}

// Same reading the server does in RecipientState: the four states partition every row.
function recipientState(recipient: Recipient): RecipientState {
  if (recipient.sent) return 'delivered';
  if (recipient.processed) return recipient.attempts > 0 ? 'failing' : 'inFlight';
  return 'pending';
}

function JobsPanel() {
  const [busyJob, setBusyJob] = useState<'enqueue' | 'fallback' | null>(null);
  const [result, setResult] = useState<string | null>(null);
  // Separate from `error`: a refused trigger is information, not a failure.
  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function trigger(kind: 'enqueue' | 'fallback') {
    setBusyJob(kind);
    setError(null);
    setResult(null);
    setNotice(null);
    const label = kind === 'enqueue' ? 'enfileiramento' : 'fallback';
    try {
      const outcome = await triggerJob(kind);
      if (outcome.started) {
        setResult(`Job de ${label} disparado. Execution id: ${outcome.executionId}`);
      } else {
        setNotice(`O job de ${label} já está em execução. Nada foi disparado.`);
      }
    } catch (err) {
      setError(describeError(err));
    } finally {
      setBusyJob(null);
    }
  }

  return (
    <div className="card">
      <div className="section-heading">
        <h2>Jobs</h2>
      </div>
      {error && <div className="error-banner">{error}</div>}
      {notice && <div className="notice-banner">{notice}</div>}
      {result && <div className="success-banner">{result}</div>}
      <div className="job-buttons">
        <button disabled={busyJob !== null} onClick={() => trigger('enqueue')}>
          {busyJob === 'enqueue' ? 'Disparando…' : 'Disparar enfileiramento'}
        </button>
        <button
          className="secondary"
          disabled={busyJob !== null}
          onClick={() => trigger('fallback')}
        >
          {busyJob === 'fallback' ? 'Disparando…' : 'Disparar fallback'}
        </button>
      </div>
    </div>
  );
}

function RecipientsPanel() {
  const [email, setEmail] = useState('');
  const [state, setState] = useState<RecipientState | ''>('');
  const [page, setPage] = useState(0);
  const [items, setItems] = useState<Recipient[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [retryingId, setRetryingId] = useState<number | null>(null);
  const [retryMessage, setRetryMessage] = useState<string | null>(null);

  function load() {
    setLoading(true);
    getRecipients({
      email: email || undefined,
      state: state || undefined,
      page,
      size: PAGE_SIZE,
    })
      .then((result) => {
        setItems(result.items);
        setTotal(result.total);
        setError(null);
      })
      .catch((err) => setError(describeError(err)))
      .finally(() => setLoading(false));
  }

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page]);

  function handleSearch(e: FormEvent) {
    e.preventDefault();
    setPage(0);
    load();
  }

  async function handleRetry(id: number) {
    setRetryingId(id);
    setRetryMessage(null);
    setError(null);
    try {
      await retryRecipient(id);
      setRetryMessage(`Destinatário ${id} marcado para reprocessamento.`);
      load();
    } catch (err) {
      setError(describeError(err));
    } finally {
      setRetryingId(null);
    }
  }

  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));

  return (
    <div className="card">
      <div className="section-heading">
        <h2>Destinatários</h2>
      </div>

      <form className="controls-row" aria-label="Buscar destinatários" onSubmit={handleSearch}>
        <input
          type="email"
          placeholder="Buscar por e-mail"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          aria-label="Buscar por e-mail"
        />
        <select
          value={state}
          onChange={(e) => setState(e.target.value as RecipientState | '')}
          aria-label="Filtrar por estado"
        >
          <option value="">Todos os estados</option>
          {RECIPIENT_STATES.map((s) => (
            <option key={s.value} value={s.value}>
              {s.label}
            </option>
          ))}
        </select>
        <button type="submit">Buscar</button>
      </form>

      {error && <div className="error-banner">{error}</div>}
      {retryMessage && <div className="success-banner">{retryMessage}</div>}
      {loading && <p className="loading">Carregando…</p>}

      {!loading && !error && items.length === 0 && (
        <p className="hint">Nenhum destinatário encontrado.</p>
      )}

      {items.length > 0 && (
        <table>
          <thead>
            <tr>
              <th>Id</th>
              <th>E-mail</th>
              <th>Mensagem</th>
              <th>Estado</th>
              <th>Criado em</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {items.map((recipient) => {
              const st = recipientState(recipient);
              return (
                <tr key={recipient.id}>
                  <td>{recipient.id}</td>
                  <td>{recipient.email}</td>
                  <td>{recipient.messageId}</td>
                  <td>
                    <span role="status" className={`badge ${st}`}>
                      {STATE_LABELS[st]}
                      {/* The count is the whole reason this state is separate from "em trânsito". */}
                      {st === 'failing' && ` (${recipient.attempts})`}
                    </span>
                  </td>
                  <td>{formatDate(recipient.createdAt)}</td>
                  <td>
                    <button
                      className="small secondary"
                      disabled={retryingId === recipient.id}
                      onClick={() => handleRetry(recipient.id)}
                    >
                      {retryingId === recipient.id ? 'Reprocessando…' : 'Reprocessar'}
                    </button>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      )}

      <div className="pagination">
        <button
          className="secondary small"
          disabled={page <= 0}
          onClick={() => setPage((p) => Math.max(0, p - 1))}
        >
          Anterior
        </button>
        <span>
          Página {page + 1} de {totalPages}
        </span>
        <button
          className="secondary small"
          disabled={page + 1 >= totalPages}
          onClick={() => setPage((p) => p + 1)}
        >
          Próxima
        </button>
      </div>
    </div>
  );
}

export default function Admin() {
  return (
    <div>
      <h2 className="page-title">Administração</h2>
      <JobsPanel />
      <RecipientsPanel />
    </div>
  );
}
