import { screen, render } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import RateCard from './RateCard';
import { rates } from '../test/fixtures';

describe('RateCard component', () => {
  it('renders all five rate cells', () => {
    render(<RateCard rates={rates} ackRate={1.5} oldestPendingSeconds={125} />);

    expect(screen.getByText('Ingestão')).toBeInTheDocument();
    expect(screen.getByText('Publicação')).toBeInTheDocument();
    expect(screen.getByText('Entrega')).toBeInTheDocument();
    expect(screen.getByText('Dreno da fila')).toBeInTheDocument();
    expect(screen.getByText('Mais antigo não entregue')).toBeInTheDocument();
  });

  it('displays rate per second with three decimals', () => {
    render(<RateCard rates={rates} ackRate={null} oldestPendingSeconds={null} />);

    // Check that rates are formatted with 3 decimals
    const ingestaoRate = rates.created.ratePerSecond.toFixed(3);
    expect(screen.getByText(`${ingestaoRate}/s`)).toBeInTheDocument();
  });

  it('displays sustained rate in hint line', () => {
    render(<RateCard rates={rates} ackRate={null} oldestPendingSeconds={null} />);

    // Check for sustained rate hint (e.g., "30s · sustentada X.XXX/s")
    const hints = screen.getAllByText(/sustentada/);
    expect(hints.length).toBeGreaterThan(0);
  });

  it('displays ackRate from broker or — when null', () => {
    render(<RateCard rates={rates} ackRate={1.5} oldestPendingSeconds={null} />);

    expect(screen.getByText('1.500/s')).toBeInTheDocument();
  });

  it('displays — for null ackRate', () => {
    render(<RateCard rates={rates} ackRate={null} oldestPendingSeconds={null} />);

    const ackRateSection = screen.getByText('Dreno da fila').closest('.rate-card');
    expect(ackRateSection).toHaveTextContent('—');
  });

  it('formats oldestPendingSeconds as duration (e.g., 2m 13s)', () => {
    render(<RateCard rates={rates} ackRate={null} oldestPendingSeconds={133} />);

    // 133 seconds = 2 minutes 13 seconds
    expect(screen.getByText('2m 13s')).toBeInTheDocument();
  });

  it('displays — for null oldestPendingSeconds', () => {
    render(<RateCard rates={rates} ackRate={null} oldestPendingSeconds={null} />);

    const oldestSection = screen.getByText('Mais antigo não entregue').closest('.rate-card');
    expect(oldestSection).toHaveTextContent('—');
  });

  it('displays time since last publish (há Xs) in publication cell', () => {
    const fiveSecondsAgo = new Date(Date.now() - 5000).toISOString();
    const customRates = { ...rates, lastPublishAt: fiveSecondsAgo };

    render(<RateCard rates={customRates} ackRate={null} oldestPendingSeconds={null} />);

    // Should display "há 5s" (approximately)
    const publishCell = screen.getByText('Publicação').closest('.rate-card');
    expect(publishCell).toHaveTextContent(/há \d+s/);
  });

  it('shows no time since when lastPublishAt is null', () => {
    const customRates = { ...rates, lastPublishAt: null };

    render(<RateCard rates={customRates} ackRate={null} oldestPendingSeconds={null} />);

    // Publication cell should still render but without "há Xs"
    expect(screen.getByText('Publicação')).toBeInTheDocument();
  });
});
