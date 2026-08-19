import type { ThroughputSeries } from '../api/client';

interface Props {
  series: ThroughputSeries[];
}

const WIDTH = 720;
const HEIGHT = 220;
const PADDING_LEFT = 40;
const PADDING_BOTTOM = 28;
const PADDING_TOP = 12;
const PADDING_RIGHT = 12;

function formatMinuteLabel(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return iso;
  return date.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
}

/**
 * Chart of throughput per minute with three series:
 * - ingestão (created_on): line
 * - publicação (published_on): bars
 * - entrega (claimed_on): line
 *
 * Null points are drawn as visible breaks. All minutes in the window are
 * present, so x-position is derived from the point's position in the series.
 */
export default function ThroughputChart({ series }: Props) {
  if (series.length === 0 || series[0].points.length === 0) {
    return <div className="chart-empty">Sem dados de vazão no período selecionado.</div>;
  }

  const points = series[0].points; // All series have the same points/minutes
  const chartWidth = WIDTH - PADDING_LEFT - PADDING_RIGHT;
  const chartHeight = HEIGHT - PADDING_TOP - PADDING_BOTTOM;
  const barGap = 2;
  const barWidth = Math.max(1, chartWidth / points.length - barGap);

  // Find max count across all series (ignoring nulls)
  let maxCount = 1;
  for (const s of series) {
    for (const p of s.points) {
      if (p.count !== null && p.count > maxCount) {
        maxCount = p.count;
      }
    }
  }

  // Find the series by stage name
  const created = series.find((s) => s.stage === 'created_on');
  const published = series.find((s) => s.stage === 'published_on');
  const claimed = series.find((s) => s.stage === 'claimed_on');

  // Show at most ~6 x-axis labels to avoid clutter
  const labelStep = Math.max(1, Math.ceil(points.length / 6));

  /**
   * X position from the point's index in the array.
   *
   * <p>This is only correct because the server emits <em>one point per minute of the window</em>,
   * gap-filled — a minute with no rows arrives as 0 and a minute before the stage existed arrives
   * as null, but every minute arrives. Index and elapsed time are therefore the same thing.
   *
   * <p>That is a load-bearing property of the wire contract, not an accident, and it used to be
   * false: the server emitted only non-empty buckets and this same arithmetic drew a 20-minute
   * gap as no gap at all. `Rates.fillGaps` on the api side is what guarantees it now. If a series
   * ever arrives sparse again, this chart will lie in exactly the old way rather than fail.
   */
  function getX(index: number): number {
    return PADDING_LEFT + index * (barWidth + barGap) + barWidth / 2;
  }

  /**
   * Calculate y position for a given count value.
   */
  function getY(count: number): number {
    if (count === null || count === 0) return PADDING_TOP + chartHeight;
    const height = (count / maxCount) * chartHeight;
    return PADDING_TOP + chartHeight - height;
  }

  /**
   * Build a path string for a line series, skipping null points.
   */
  function buildLinePath(s: ThroughputSeries | undefined): string {
    if (!s) return '';
    const segments: string[] = [];
    let inLine = false;
    for (let i = 0; i < s.points.length; i++) {
      const p = s.points[i];
      const x = getX(i);
      const y = getY(p.count ?? 0);
      if (p.count !== null) {
        if (!inLine) {
          segments.push(`M ${x} ${y}`);
          inLine = true;
        } else {
          segments.push(`L ${x} ${y}`);
        }
      } else {
        inLine = false;
      }
    }
    return segments.join(' ');
  }

  return (
    <>
      <svg
        viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
        width="100%"
        role="img"
        aria-label="Gráfico de mensagens por minuto (ingestão, publicação, entrega)"
      >
        {/* Axes */}
        <line
          x1={PADDING_LEFT}
          y1={PADDING_TOP}
          x2={PADDING_LEFT}
          y2={PADDING_TOP + chartHeight}
          stroke="var(--color-border)"
        />
        <line
          x1={PADDING_LEFT}
          y1={PADDING_TOP + chartHeight}
          x2={WIDTH - PADDING_RIGHT}
          y2={PADDING_TOP + chartHeight}
          stroke="var(--color-border)"
        />

        {/* Y-axis labels */}
        <text x={4} y={PADDING_TOP + 4} fontSize="10" fill="var(--color-text-muted)">
          {maxCount}
        </text>
        <text x={4} y={PADDING_TOP + chartHeight} fontSize="10" fill="var(--color-text-muted)">
          0
        </text>

        {/* Bars for publicação (published_on) */}
        {published?.points.map((point, index) => {
          if (point.count === null || point.count === 0) return null;
          const x = PADDING_LEFT + index * (barWidth + barGap);
          const barHeight = (point.count / maxCount) * chartHeight;
          const y = PADDING_TOP + chartHeight - barHeight;
          return (
            <g key={`published-${index}`}>
              <rect
                x={x}
                y={y}
                width={barWidth}
                height={Math.max(0, barHeight)}
                fill="var(--color-chart-published)"
              >
                <title>
                  {formatMinuteLabel(point.minute)}: {point.count} publicadas
                </title>
              </rect>
            </g>
          );
        })}

        {/* Lines for ingestão (created_on) */}
        {created && (
          <path
            d={buildLinePath(created)}
            stroke="var(--color-chart-created)"
            strokeWidth="2"
            fill="none"
          />
        )}

        {/* Lines for entrega (claimed_on) */}
        {claimed && (
          <path
            d={buildLinePath(claimed)}
            stroke="var(--color-chart-claimed)"
            strokeWidth="2"
            fill="none"
          />
        )}

        {/* X-axis labels and vertical separators for null points */}
        {points.map((point, index) => {
          const showLabel = index % labelStep === 0;
          const x = PADDING_LEFT + index * (barWidth + barGap);

          return (
            <g key={point.minute}>
              {showLabel && (
                <text
                  x={x + barWidth / 2}
                  y={HEIGHT - 8}
                  fontSize="9"
                  textAnchor="middle"
                  fill="var(--color-text-muted)"
                >
                  {formatMinuteLabel(point.minute)}
                </text>
              )}

              {/* Draw a thin vertical line to mark null points */}
              {point.count === null && (
                <line
                  x1={x}
                  y1={PADDING_TOP}
                  x2={x}
                  y2={PADDING_TOP + chartHeight}
                  stroke="var(--color-border)"
                  strokeDasharray="2,2"
                  opacity="0.5"
                />
              )}
            </g>
          );
        })}
      </svg>

      <p className="hint">
        O enfileirador roda a cada 30s — vales no gráfico de publicação são o intervalo do
        agendador, não uma parada.
      </p>
    </>
  );
}
