import { useEffect, useState } from 'react';
import { ApiError, getMessages, type MessageSummary } from '../api/client';

const PAGE_SIZE = 20;

function describeError(err: unknown): string {
  if (err instanceof ApiError) return err.message;
  if (err instanceof Error) return err.message;
  return 'Erro desconhecido ao carregar o histórico.';
}

function formatDate(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return iso;
  return date.toLocaleString('pt-BR');
}

export default function History() {
  const [query, setQuery] = useState('');
  const [debouncedQuery, setDebouncedQuery] = useState('');
  const [page, setPage] = useState(0);
  const [items, setItems] = useState<MessageSummary[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setDebouncedQuery(query);
      setPage(0);
    }, 300);
    return () => window.clearTimeout(timer);
  }, [query]);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    getMessages({ page, size: PAGE_SIZE, q: debouncedQuery || undefined })
      .then((result) => {
        if (cancelled) return;
        setItems(result.items);
        setTotal(result.total);
        setError(null);
      })
      .catch((err) => {
        if (cancelled) return;
        setError(describeError(err));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [page, debouncedQuery]);

  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));

  return (
    <div>
      <h2 className="page-title">Histórico de mensagens</h2>

      <div className="controls-row">
        <input
          type="search"
          placeholder="Buscar por título…"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          style={{ minWidth: '260px' }}
          aria-label="Buscar mensagens"
        />
      </div>

      {error && <div className="error-banner">{error}</div>}
      {loading && <p className="loading">Carregando…</p>}

      {!loading && !error && items.length === 0 && (
        <p className="hint">Nenhuma mensagem encontrada.</p>
      )}

      {items.length > 0 && (
        <div className="card">
          <table>
            <thead>
              <tr>
                <th>Id</th>
                <th>Título</th>
                <th>Tipo</th>
                <th>Criada em</th>
                <th>Progresso</th>
              </tr>
            </thead>
            <tbody>
              {items.map((item) => {
                const ratio =
                  item.recipientCount > 0 ? item.sentCount / item.recipientCount : 0;
                return (
                  <tr key={item.id}>
                    <td>{item.id}</td>
                    <td>{item.title}</td>
                    <td>{item.contentType}</td>
                    <td>{formatDate(item.createdAt)}</td>
                    <td>
                      <div
                        className="progress-track"
                        role="progressbar"
                        aria-label={`Progresso da mensagem ${item.title}`}
                        aria-valuenow={item.sentCount}
                        aria-valuemin={0}
                        aria-valuemax={item.recipientCount}
                      >
                        <div
                          className="progress-fill"
                          style={{ width: `${Math.round(ratio * 100)}%` }}
                        />
                      </div>
                      <span className="hint">
                        {item.sentCount} / {item.recipientCount}
                      </span>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
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
