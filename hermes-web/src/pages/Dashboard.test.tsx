import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import Dashboard from './Dashboard';
import { renderWithRouter, mockFetchRoutes, jsonResponse } from '../test/helpers';
import { adminStats, brokerStatus, throughput } from '../test/fixtures';

describe('Dashboard page', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    vi.useRealTimers();
  });

  it('shows the failing count apart from in flight', async () => {
    // A message stuck in a retry loop and one merely waiting in the queue both read
    // processed=true, sent=false. Before the failing bucket existed they were one number.
    const fetchMock = mockFetchRoutes({
      '/api/admin/stats': { ...adminStats, inFlight: 5, failing: 2 },
      '/api/admin/broker': brokerStatus,
      '/api/admin/throughput': throughput,
    });
    vi.stubGlobal('fetch', fetchMock);

    renderWithRouter(<Dashboard />);

    await waitFor(() => {
      // selector: 'dt' because both labels also appear in the explanatory paragraph below.
      expect(screen.getByText('Falhando', { selector: 'dt' }).closest('dl')).toHaveTextContent('2');
    });
    expect(screen.getByText('Em trânsito', { selector: 'dt' }).closest('dl')).toHaveTextContent('5');
  });

  it('renders stat values, broker info, and throughput chart after loading', async () => {
    const fetchMock = mockFetchRoutes({
      '/api/admin/stats': adminStats,
      '/api/admin/broker': brokerStatus,
      '/api/admin/throughput': throughput,
    });
    vi.stubGlobal('fetch', fetchMock);

    renderWithRouter(<Dashboard />);

    await waitFor(() => {
      expect(screen.getByText('Pendentes')).toBeInTheDocument();
    });

    // Stats are rendered as dl/dt/dd elements; verify the values are present
    expect(screen.getByText('3')).toBeInTheDocument();  // Pendentes value
    expect(screen.getByText('2')).toBeInTheDocument();  // Em trânsito value
    expect(screen.getByText('10')).toBeInTheDocument(); // Entregues value
    expect(screen.getByText('15')).toBeInTheDocument(); // Total de mensagens value

    expect(screen.getByText('artemis')).toBeInTheDocument();
    expect(screen.getByRole('img', { name: /Gráfico de mensagens/ })).toBeInTheDocument();
  });

  it('can change refresh interval from 5s to 2s to off', async () => {
    const user = userEvent.setup();
    const fetchMock = mockFetchRoutes({
      '/api/admin/stats': adminStats,
      '/api/admin/broker': brokerStatus,
      '/api/admin/throughput': throughput,
    });
    vi.stubGlobal('fetch', fetchMock);

    renderWithRouter(<Dashboard />);

    await waitFor(() => {
      expect(screen.getByText('Pendentes')).toBeInTheDocument();
    });

    // Verify initial interval is 5s
    expect(screen.getByDisplayValue('5s')).toBeInTheDocument();

    // Change interval to 2s
    const select = screen.getByDisplayValue('5s');
    await user.selectOptions(select, '2s');

    // Verify select changed
    expect(screen.getByDisplayValue('2s')).toBeInTheDocument();

    // Change to off
    await user.selectOptions(screen.getByDisplayValue('2s'), 'Desligado');

    // Verify select changed to off
    expect(screen.getByDisplayValue('Desligado')).toBeInTheDocument();
  });

  it('displays queue and DLQ depth, with — when null', async () => {
    const fetchMock = mockFetchRoutes({
      '/api/admin/stats': adminStats,
      '/api/admin/broker': { kind: 'artemis', queueDepth: null, dlqDepth: null, error: null },
      '/api/admin/throughput': throughput,
    });
    vi.stubGlobal('fetch', fetchMock);

    renderWithRouter(<Dashboard />);

    await waitFor(() => {
      expect(screen.getByText('Profundidade da fila: —')).toBeInTheDocument();
      expect(screen.getByText('Profundidade da DLQ: —')).toBeInTheDocument();
    });
  });

  it('shows broker error inside the broker card when brokerStatus.error is set', async () => {
    const fetchMock = mockFetchRoutes({
      '/api/admin/stats': adminStats,
      '/api/admin/broker': {
        kind: 'artemis',
        queueDepth: 5,
        dlqDepth: 0,
        error: 'Broker connection failed',
      },
      '/api/admin/throughput': throughput,
    });
    vi.stubGlobal('fetch', fetchMock);

    renderWithRouter(<Dashboard />);

    await waitFor(() => {
      expect(screen.getByText('Broker connection failed')).toBeInTheDocument();
    });

    // Rest of page should still render
    expect(screen.getByText('Pendentes')).toBeInTheDocument();
  });

  it('oneFailedCallFailsTheWholeDashboard_currentBehaviour', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.includes('/api/admin/stats')) return jsonResponse(adminStats);
      if (url.includes('/api/admin/broker')) return jsonResponse(brokerStatus);
      if (url.includes('/api/admin/throughput')) {
        return jsonResponse({}, { status: 500, ok: false });
      }
      throw new Error(`unexpected url: ${url}`);
    });
    vi.stubGlobal('fetch', fetchMock);

    renderWithRouter(<Dashboard />);

    // Promise.all rejects if any call fails, so whole panel shows error
    await waitFor(() => {
      expect(screen.getByText(/status 500/)).toBeInTheDocument();
    });

    // Stats are NOT rendered because Promise.all failed
    const pendingCard = screen.queryByText('Pendentes');
    expect(pendingCard).not.toBeInTheDocument();
  });
});
