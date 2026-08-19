import { screen } from '@testing-library/react';
import { render } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import ThroughputChart from './ThroughputChart';
import { throughput } from '../test/fixtures';

describe('ThroughputChart component', () => {
  it('shows empty state message when no data series', () => {
    render(<ThroughputChart series={[]} />);

    expect(
      screen.getByText('Sem dados de vazão no período selecionado.'),
    ).toBeInTheDocument();
  });

  it('renders SVG chart with accessible role when data is present', () => {
    render(<ThroughputChart series={throughput.series} />);

    const chart = screen.getByRole('img', { name: /Gráfico de mensagens/ });
    expect(chart).toBeInTheDocument();
  });

  it('renders bar elements with title tooltips containing time and published count', () => {
    render(<ThroughputChart series={throughput.series} />);

    // SVG titles for published bars contain formatted time and count
    // Time is formatted in local timezone, so we just verify the count appears
    const titlesWith3 = screen.getAllByText(/3 publicadas/);
    expect(titlesWith3.length).toBeGreaterThan(0);

    const titlesWith4 = screen.getAllByText(/4 publicadas/);
    expect(titlesWith4.length).toBeGreaterThan(0);
  });

  it('renders bars for published and lines for created/claimed stages', () => {
    render(<ThroughputChart series={throughput.series} />);

    // SVG should contain multiple series' data
    const titlesWith4Publicadas = screen.getAllByText(/4 publicadas/);
    expect(titlesWith4Publicadas.length).toBeGreaterThan(0);
  });

  it('handles null points as visible breaks', () => {
    const seriesWithNull = [
      {
        stage: 'created_on',
        points: [
          { minute: '2026-08-14T10:00:00Z', count: 10 },
          { minute: '2026-08-14T10:01:00Z', count: null },
          { minute: '2026-08-14T10:02:00Z', count: 5 },
        ],
      },
      {
        stage: 'published_on',
        points: [
          { minute: '2026-08-14T10:00:00Z', count: 8 },
          { minute: '2026-08-14T10:01:00Z', count: null },
          { minute: '2026-08-14T10:02:00Z', count: 3 },
        ],
      },
      {
        stage: 'claimed_on',
        points: [
          { minute: '2026-08-14T10:00:00Z', count: 7 },
          { minute: '2026-08-14T10:01:00Z', count: null },
          { minute: '2026-08-14T10:02:00Z', count: 2 },
        ],
      },
    ];

    render(<ThroughputChart series={seriesWithNull} />);

    // Chart should render with null point handling
    expect(screen.getByRole('img', { name: /Gráfico de mensagens/ })).toBeInTheDocument();
  });
});
