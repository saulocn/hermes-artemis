import { screen } from '@testing-library/react';
import { render } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import ThroughputChart from './ThroughputChart';
import { throughput } from '../test/fixtures';

describe('ThroughputChart component', () => {
  it('shows empty state message when no data points', () => {
    render(<ThroughputChart points={[]} />);

    expect(
      screen.getByText('Sem dados de vazão no período selecionado.'),
    ).toBeInTheDocument();
  });

  it('renders SVG chart with accessible role when data is present', () => {
    render(<ThroughputChart points={throughput.points} />);

    const chart = screen.getByRole('img', { name: /Gráfico de mensagens/ });
    expect(chart).toBeInTheDocument();
  });

  it('renders bar elements with title tooltips containing time and delivery count', () => {
    render(<ThroughputChart points={throughput.points} />);

    // SVG titles contain formatted time and delivery count; check by delivery count
    // Time is formatted in local timezone, so we just verify the delivery count appears
    const titlesWith4 = screen.getAllByText(/4 entregues/);
    expect(titlesWith4.length).toBeGreaterThan(0);

    const titlesWith5 = screen.getAllByText(/5 entregues/);
    expect(titlesWith5.length).toBeGreaterThan(0);
  });

  it('renders all bars for all data points', () => {
    const points = [
      { minute: '2026-08-14T10:00:00Z', delivered: 10 },
      { minute: '2026-08-14T10:01:00Z', delivered: 15 },
      { minute: '2026-08-14T10:02:00Z', delivered: 5 },
    ];

    render(<ThroughputChart points={points} />);

    // Check for each delivery count in the rendered titles
    const titlesContaining10 = screen.getAllByText(/10 entregues/);
    expect(titlesContaining10.length).toBeGreaterThan(0);

    const titlesContaining15 = screen.getAllByText(/15 entregues/);
    expect(titlesContaining15.length).toBeGreaterThan(0);

    const titlesContaining5 = screen.getAllByText(/5 entregues/);
    expect(titlesContaining5.length).toBeGreaterThan(0);
  });
});
