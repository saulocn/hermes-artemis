import type { RatesResponse } from '../api/client';

interface Props {
  rates: RatesResponse;
  ackRate: number | null;
  oldestPendingSeconds: number | null;
}

/**
 * Format seconds as human-readable duration (e.g., "2m 13s").
 */
function formatDuration(seconds: number): string {
  const minutes = Math.floor(seconds / 60);
  const secs = Math.floor(seconds % 60);
  if (minutes > 0) {
    return `${minutes}m ${secs}s`;
  }
  return `${secs}s`;
}

/**
 * Format a rate with two numbers: ratePerSecond and sustainedPerSecond.
 * Returns the main rate and a hint line with window and sustained rate.
 */
function formatRate(rate: {
  ratePerSecond: number;
  sustainedPerSecond: number | null;
  window: number;
}): { main: string; hint: string } {
  const main = rate.ratePerSecond.toFixed(3);
  const sustained =
    rate.sustainedPerSecond !== null
      ? `sustentada ${rate.sustainedPerSecond.toFixed(3)}/s`
      : '';
  const hint = `${rate.window}s · ${sustained}`.replace(/\s+·\s+$/, '');
  return { main, hint };
}

/**
 * Format oldestPendingSeconds as a duration, or "—" if null.
 */
function formatOldest(seconds: number | null): string {
  if (seconds === null) return '—';
  return formatDuration(seconds);
}

/**
 * Format time since lastPublishAt (relative time).
 */
function formatTimeSince(iso: string | null): string {
  if (!iso) return '';
  const publishAt = new Date(iso);
  const now = new Date();
  const seconds = Math.floor((now.getTime() - publishAt.getTime()) / 1000);
  if (seconds < 0) return '';
  return `há ${formatDuration(seconds)}`;
}

export default function RateCard({ rates, ackRate, oldestPendingSeconds }: Props) {
  const createdRate = formatRate(rates.created);
  const publishedRate = formatRate(rates.published);
  const claimedRate = formatRate(rates.claimed);
  const timeSincePublished = formatTimeSince(rates.lastPublishAt);
  const oldest = formatOldest(oldestPendingSeconds);
  const ackRateStr = ackRate !== null ? ackRate.toFixed(3) : '—';

  return (
    <div className="card">
      <div className="section-heading">
        <h2>Vazão agora</h2>
      </div>
      <div className="rate-grid">
        <div className="rate-card">
          <dt>Ingestão</dt>
          <dd>{createdRate.main}/s</dd>
          <p className="rate-hint">{createdRate.hint}</p>
        </div>

        <div className="rate-card">
          <dt>Publicação</dt>
          <dd>{publishedRate.main}/s</dd>
          <p className="rate-hint">{publishedRate.hint}</p>
          {timeSincePublished && <p className="rate-time">{timeSincePublished}</p>}
        </div>

        <div className="rate-card">
          <dt>Entrega</dt>
          <dd>{claimedRate.main}/s</dd>
          <p className="rate-hint">{claimedRate.hint}</p>
        </div>

        <div className="rate-card">
          <dt>Dreno da fila</dt>
          <dd>{ackRateStr}/s</dd>
          <p className="rate-hint">do broker</p>
        </div>

        <div className="rate-card">
          <dt>Mais antigo não entregue</dt>
          <dd>{oldest}</dd>
          <p className="rate-hint">desde criação</p>
        </div>
      </div>
      <p className="hint">
        <strong>Taxa/s</strong> divide o total pela janela inteira (inclui inatividade);
        <strong>sustentada</strong> divide apenas pelo tempo entre o primeiro e último envio.
        Veja a comparação com a fila no benchmark.
      </p>
    </div>
  );
}
