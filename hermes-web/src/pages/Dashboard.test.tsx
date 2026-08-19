import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import Dashboard from './Dashboard';
import { renderWithRouter, mockFetchRoutes, jsonResponse } from '../test/helpers';
import { adminStats, brokerStatus, throughput, rates, idleRates } from '../test/fixtures';

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
      '/api/admin/rates': rates,
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
      '/api/admin/rates': rates,
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
      '/api/admin/rates': rates,
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
      '/api/admin/broker': { kind: 'artemis', queueDepth: null, dlqDepth: null, ackRate: null, error: null },
      '/api/admin/rates': rates,
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
        ackRate: 1.5,
        error: 'Broker connection failed',
      },
      '/api/admin/rates': rates,
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

  it('one failing endpoint degrades only its own card with Promise.allSettled', async () => {
    // Rates endpoint fails, but stats and broker should still render
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.includes('/api/admin/stats')) return jsonResponse(adminStats);
      if (url.includes('/api/admin/broker')) return jsonResponse(brokerStatus);
      if (url.includes('/api/admin/rates')) {
        return jsonResponse({}, { status: 500, ok: false });
      }
      if (url.includes('/api/admin/throughput')) return jsonResponse(throughput);
      throw new Error(`unexpected url: ${url}`);
    });
    vi.stubGlobal('fetch', fetchMock);

    renderWithRouter(<Dashboard />);

    // Rates endpoint error is shown
    await waitFor(() => {
      expect(screen.getByText(/status 500/)).toBeInTheDocument();
    });

    // But stats are still rendered
    expect(screen.getByText('Pendentes')).toBeInTheDocument();
    expect(screen.getByText('3')).toBeInTheDocument();  // Pendentes value

    // And broker is still rendered
    expect(screen.getByText('artemis')).toBeInTheDocument();
  });

  it('throughput endpoint failure shows error banner while chart is hidden', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.includes('/api/admin/stats')) return jsonResponse(adminStats);
      if (url.includes('/api/admin/broker')) return jsonResponse(brokerStatus);
      if (url.includes('/api/admin/rates')) return jsonResponse(rates);
      if (url.includes('/api/admin/throughput')) {
        return jsonResponse({}, { status: 500, ok: false });
      }
      throw new Error(`unexpected url: ${url}`);
    });
    vi.stubGlobal('fetch', fetchMock);

    renderWithRouter(<Dashboard />);

    // Throughput error is shown
    await waitFor(() => {
      const errors = screen.getAllByText(/status 500/);
      expect(errors.length).toBeGreaterThan(0);
    });

    // But rates and stats are still rendered
    expect(screen.getByText('Ingestão')).toBeInTheDocument();
    expect(screen.getByText('Pendentes')).toBeInTheDocument();
  });

  it('renders rate card with stats and broker data', async () => {
    const fetchMock = mockFetchRoutes({
      '/api/admin/stats': adminStats,
      '/api/admin/broker': brokerStatus,
      '/api/admin/rates': rates,
      '/api/admin/throughput': throughput,
    });
    vi.stubGlobal('fetch', fetchMock);

    renderWithRouter(<Dashboard />);

    await waitFor(() => {
      expect(screen.getByText('Vazão agora')).toBeInTheDocument();
    });

    // All five rate cells should be present
    expect(screen.getByText('Ingestão')).toBeInTheDocument();
    expect(screen.getByText('Publicação')).toBeInTheDocument();
    expect(screen.getByText('Entrega')).toBeInTheDocument();
    expect(screen.getByText('Dreno da fila')).toBeInTheDocument();
    expect(screen.getByText('Mais antigo não entregue')).toBeInTheDocument();
  });

  it('stats failure shows error while rates and broker still render', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.includes('/api/admin/stats')) {
        return jsonResponse({}, { status: 500, ok: false });
      }
      if (url.includes('/api/admin/broker')) return jsonResponse(brokerStatus);
      if (url.includes('/api/admin/rates')) return jsonResponse(rates);
      if (url.includes('/api/admin/throughput')) return jsonResponse(throughput);
      throw new Error(`unexpected url: ${url}`);
    });
    vi.stubGlobal('fetch', fetchMock);

    renderWithRouter(<Dashboard />);

    // Stats error is shown
    await waitFor(() => {
      const errors = screen.getAllByText(/status 500/);
      expect(errors.length).toBeGreaterThan(0);
    });

    // But rate card and broker are still rendered
    expect(screen.getByText('Vazão agora')).toBeInTheDocument();
    expect(screen.getByText('artemis')).toBeInTheDocument();
  });

  it('renders rate card with idle pipeline (absent fields normalised to null)', async () => {
    // An idle pipeline omits span and sustainedPerSecond from the server response. The client's
    // orNull normaliser converts these absent fields to null so downstream code sees the declared
    // types, not undefined. This test exercises the real shape: the server never sends a rates
    // response with every field present unless messages are actually flowing.
    const fetchMock = mockFetchRoutes({
      '/api/admin/stats': adminStats,
      '/api/admin/broker': brokerStatus,
      '/api/admin/rates': idleRates,
      '/api/admin/throughput': throughput,
    });
    vi.stubGlobal('fetch', fetchMock);

    renderWithRouter(<Dashboard />);

    await waitFor(() => {
      expect(screen.getByText('Vazão agora')).toBeInTheDocument();
    });

    // All five rate cells should render without errors, even though span and sustainedPerSecond
    // are absent from the server response and normalised to null.
    expect(screen.getByText('Ingestão')).toBeInTheDocument();
    expect(screen.getByText('Publicação')).toBeInTheDocument();
    expect(screen.getByText('Entrega')).toBeInTheDocument();
    expect(screen.getByText('Dreno da fila')).toBeInTheDocument();
    expect(screen.getByText('Mais antigo não entregue')).toBeInTheDocument();
  });
});
