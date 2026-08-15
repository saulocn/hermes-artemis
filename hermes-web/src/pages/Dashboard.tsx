import { useEffect, useRef, useState } from 'react';
import {
  ApiError,
  getAdminStats,
  getBrokerStatus,
  getThroughput,
  type AdminStats,
  type BrokerStatus,
  type ThroughputPoint,
} from '../api/client';
import ThroughputChart from '../components/ThroughputChart';

type IntervalOption = 'off' | '2000' | '5000' | '15000';

const INTERVAL_LABELS: Record<IntervalOption, string> = {
  off: 'Desligado',
  '2000': '2s',
  '5000': '5s',
  '15000': '15s',
};

function describeError(err: unknown): string {
  if (err instanceof ApiError) return err.message;
  if (err instanceof Error) return err.message;
  return 'Erro desconhecido ao carregar dados.';
}

export default function Dashboard() {
  const [stats, setStats] = useState<AdminStats | null>(null);
  const [broker, setBroker] = useState<BrokerStatus | null>(null);
  const [points, setPoints] = useState<ThroughputPoint[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [intervalOption, setIntervalOption] = useState<IntervalOption>('5000');
  const isFirstLoad = useRef(true);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      try {
        const [statsResult, brokerResult, throughputResult] = await Promise.all([
          getAdminStats(),
          getBrokerStatus(),
          getThroughput(60),
        ]);
        if (cancelled) return;
        setStats(statsResult);
        setBroker(brokerResult);
        setPoints(throughputResult.points);
        setError(null);
      } catch (err) {
        if (cancelled) return;
        setError(describeError(err));
      } finally {
        if (!cancelled) {
          setLoading(false);
          isFirstLoad.current = false;
        }
      }
    }

    load();

    if (intervalOption === 'off') {
      return () => {
        cancelled = true;
      };
    }

    const timer = window.setInterval(load, Number(intervalOption));
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [intervalOption]);

  return (
    <div>
      <h2 className="page-title">Painel</h2>

      <div className="controls-row">
        <label htmlFor="refresh-interval" style={{ margin: 0 }}>
          Atualização automática:
        </label>
        <select
          id="refresh-interval"
          value={intervalOption}
          onChange={(e) => setIntervalOption(e.target.value as IntervalOption)}
        >
          {(Object.keys(INTERVAL_LABELS) as IntervalOption[]).map((option) => (
            <option key={option} value={option}>
              {INTERVAL_LABELS[option]}
            </option>
          ))}
        </select>
      </div>

      {error && <div className="error-banner">{error}</div>}
      {loading && <p className="loading">Carregando…</p>}

      {stats && (
        <div className="stat-grid">
          <dl className="stat-card">
            <dt>Pendentes</dt>
            <dd>{stats.pending}</dd>
          </dl>
          <dl className="stat-card">
            <dt>Em trânsito</dt>
            <dd>{stats.inFlight}</dd>
          </dl>
          <dl className="stat-card">
            <dt>Entregues</dt>
            <dd>{stats.delivered}</dd>
          </dl>
          <dl className="stat-card">
            <dt>Total de mensagens</dt>
            <dd>{stats.totalMessages}</dd>
          </dl>
        </div>
      )}

      <p className="hint">
        <strong>Em trânsito</strong> significa que o destinatário já foi publicado no broker de
        mensagens, mas ainda não foi confirmado como entregue pelo mailer.
      </p>

      <div className="card">
        <div className="section-heading">
          <h2>Broker de mensagens</h2>
        </div>
        {broker ? (
          <div>
            <p>
              Tipo: <strong>{broker.kind}</strong>
            </p>
            <p>Profundidade da fila: {broker.queueDepth ?? '—'}</p>
            <p>Profundidade da DLQ: {broker.dlqDepth ?? '—'}</p>
            {broker.error && <div className="error-banner">{broker.error}</div>}
          </div>
        ) : (
          !loading && <p className="hint">Sem dados do broker.</p>
        )}
      </div>

      <div className="card">
        <div className="section-heading">
          <h2>Vazão (últimos 60 minutos)</h2>
        </div>
        <ThroughputChart points={points} />
      </div>
    </div>
  );
}
