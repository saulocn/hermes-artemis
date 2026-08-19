import { useEffect, useRef, useState } from 'react';
import {
  ApiError,
  getAdminStats,
  getBrokerStatus,
  getRates,
  getThroughput,
  type AdminStats,
  type BrokerStatus,
  type RatesResponse,
  type ThroughputSeries,
} from '../api/client';
import RateCard from '../components/RateCard';
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
  const [statsError, setStatsError] = useState<string | null>(null);
  const [broker, setBroker] = useState<BrokerStatus | null>(null);
  const [brokerError, setBrokerError] = useState<string | null>(null);
  const [rates, setRates] = useState<RatesResponse | null>(null);
  const [ratesError, setRatesError] = useState<string | null>(null);
  const [series, setSeries] = useState<ThroughputSeries[]>([]);
  const [throughputError, setThroughputError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [intervalOption, setIntervalOption] = useState<IntervalOption>('5000');
  const isFirstLoad = useRef(true);
  const throughputTimerRef = useRef<number | null>(null);

  // Effect 1: Load stats, broker, and rates on the selected interval
  useEffect(() => {
    let cancelled = false;

    async function load() {
      try {
        const results = await Promise.allSettled([
          getAdminStats(),
          getBrokerStatus(),
          getRates(30),
        ]);

        if (cancelled) return;

        // Handle stats
        const statsResult = results[0];
        if (statsResult.status === 'fulfilled') {
          setStats(statsResult.value);
          setStatsError(null);
        } else {
          setStats(null);
          setStatsError(describeError(statsResult.reason));
        }

        // Handle broker
        const brokerResult = results[1];
        if (brokerResult.status === 'fulfilled') {
          setBroker(brokerResult.value);
          setBrokerError(null);
        } else {
          setBroker(null);
          setBrokerError(describeError(brokerResult.reason));
        }

        // Handle rates
        const ratesResult = results[2];
        if (ratesResult.status === 'fulfilled') {
          setRates(ratesResult.value);
          setRatesError(null);
        } else {
          setRates(null);
          setRatesError(describeError(ratesResult.reason));
        }
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

  // Effect 2: Load throughput on a fixed 60s timer (independent of intervalOption)
  useEffect(() => {
    let cancelled = false;

    async function loadThroughput() {
      try {
        const result = await getThroughput(60);
        if (cancelled) return;
        setSeries(result.series);
        setThroughputError(null);
      } catch (err) {
        if (cancelled) return;
        setSeries([]);
        setThroughputError(describeError(err));
      }
    }

    loadThroughput();

    const timer = window.setInterval(loadThroughput, 60000);
    throughputTimerRef.current = timer as any;

    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, []);

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

      {loading && <p className="loading">Carregando…</p>}

      {rates && !ratesError ? (
        <RateCard
          rates={rates}
          ackRate={broker?.ackRate ?? null}
          oldestPendingSeconds={stats?.oldestPendingSeconds ?? null}
        />
      ) : (
        ratesError && <div className="error-banner">{ratesError}</div>
      )}

      {stats && !statsError ? (
        <div className="stat-grid">
          <dl className="stat-card">
            <dt>Pendentes</dt>
            <dd>{stats.pending}</dd>
          </dl>
          <dl className="stat-card">
            <dt>Em trânsito</dt>
            <dd>{stats.inFlight}</dd>
          </dl>
          <dl className={stats.failing > 0 ? 'stat-card stat-card-alert' : 'stat-card'}>
            <dt>Falhando</dt>
            <dd>{stats.failing}</dd>
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
      ) : (
        statsError && <div className="error-banner">{statsError}</div>
      )}

      <p className="hint">
        <strong>Em trânsito</strong> significa que o destinatário já foi publicado no broker de
        mensagens, mas ainda não foi confirmado como entregue pelo mailer, e nenhum envio falhou.{' '}
        <strong>Falhando</strong> é a parte de publicados e não entregues cujo envio já lançou erro
        ao menos uma vez.
      </p>

      <div className="card">
        <div className="section-heading">
          <h2>Broker de mensagens</h2>
        </div>
        {broker && !brokerError ? (
          <div>
            <p>
              Tipo: <strong>{broker.kind}</strong>
            </p>
            <p>Profundidade da fila: {broker.queueDepth ?? '—'}</p>
            <p>Profundidade da DLQ: {broker.dlqDepth ?? '—'}</p>
            {broker.error && <div className="error-banner">{broker.error}</div>}
          </div>
        ) : (
          brokerError && <div className="error-banner">{brokerError}</div>
        )}
      </div>

      <div className="card">
        <div className="section-heading">
          <h2>Vazão por minuto (últimos 60 minutos)</h2>
        </div>
        {throughputError ? (
          <div className="error-banner">{throughputError}</div>
        ) : (
          <>
            <ThroughputChart series={series} />
            <p className="hint">O minuto em progresso é excluído do gráfico.</p>
          </>
        )}
      </div>
    </div>
  );
}
