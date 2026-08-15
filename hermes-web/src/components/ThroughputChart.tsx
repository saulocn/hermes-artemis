import type { ThroughputPoint } from '../api/client';

interface Props {
  points: ThroughputPoint[];
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

/** Simple bar chart of deliveries per minute, drawn as inline SVG. */
export default function ThroughputChart({ points }: Props) {
  if (points.length === 0) {
    return <div className="chart-empty">Sem dados de vazão no período selecionado.</div>;
  }

  const maxDelivered = Math.max(1, ...points.map((p) => p.delivered));
  const chartWidth = WIDTH - PADDING_LEFT - PADDING_RIGHT;
  const chartHeight = HEIGHT - PADDING_TOP - PADDING_BOTTOM;
  const barGap = 2;
  const barWidth = Math.max(1, chartWidth / points.length - barGap);

  // Show at most ~6 x-axis labels to avoid clutter.
  const labelStep = Math.max(1, Math.ceil(points.length / 6));

  return (
    <svg
      viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
      width="100%"
      role="img"
      aria-label="Gráfico de mensagens entregues por minuto"
    >
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
      <text x={4} y={PADDING_TOP + 4} fontSize="10" fill="var(--color-text-muted)">
        {maxDelivered}
      </text>
      <text x={4} y={PADDING_TOP + chartHeight} fontSize="10" fill="var(--color-text-muted)">
        0
      </text>
      {points.map((point, index) => {
        const barHeight = (point.delivered / maxDelivered) * chartHeight;
        const x = PADDING_LEFT + index * (barWidth + barGap);
        const y = PADDING_TOP + chartHeight - barHeight;
        const showLabel = index % labelStep === 0;
        return (
          <g key={point.minute}>
            <rect
              x={x}
              y={y}
              width={barWidth}
              height={Math.max(0, barHeight)}
              fill="var(--color-chart-bar)"
            >
              <title>
                {formatMinuteLabel(point.minute)}: {point.delivered} entregues
              </title>
            </rect>
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
          </g>
        );
      })}
    </svg>
  );
}
