import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import Admin from './Admin';
import { renderWithRouter, mockFetchRoutes, expectRequest, jsonResponse } from '../test/helpers';
import { recipientsPage, jobTriggerResponse, retryResponse } from '../test/fixtures';

describe('Admin page', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('renders job buttons and the recipients table after loading', async () => {
    const fetchMock = mockFetchRoutes({
      '/api/admin/recipients': recipientsPage,
    });
    vi.stubGlobal('fetch', fetchMock);

    renderWithRouter(<Admin />);

    expect(screen.getByRole('button', { name: 'Disparar enfileiramento' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Disparar fallback' })).toBeInTheDocument();

    await waitFor(() =>
      expect(screen.getByText('ana@example.com')).toBeInTheDocument(),
    );
    expect(screen.getByRole('button', { name: 'Reprocessar' })).toBeInTheDocument();
  });

  it('shows an error banner when the recipients search fails', async () => {
    const fetchMock = vi.fn(async () =>
      jsonResponse({}, { status: 500, ok: false }),
    );
    vi.stubGlobal('fetch', fetchMock);

    renderWithRouter(<Admin />);

    await waitFor(() =>
      expect(screen.getByText(/status 500/)).toBeInTheDocument(),
    );
  });

  it('states plainly that a job is already running instead of showing a failure', async () => {
    // The server answers 409 when a run is in flight. This used to reach the operator as a red
    // banner containing the raw JSON body — the intent to say "already running" was written in
    // three Java comments and never realised on screen.
    const user = userEvent.setup();
    const fetchMock = mockFetchRoutes({
      '/api/admin/recipients': recipientsPage,
      '/api/admin/jobs/enqueue': { body: { error: 'job already running' }, status: 409 },
    });
    vi.stubGlobal('fetch', fetchMock);

    renderWithRouter(<Admin />);
    await user.click(screen.getByRole('button', { name: 'Disparar enfileiramento' }));

    await waitFor(() => {
      expect(screen.getByText(/já está em execução/)).toBeInTheDocument();
    });
    expect(screen.queryByText(/status 409/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Execution id/)).not.toBeInTheDocument();
  });

  it('triggers enqueue job and shows execution id banner', async () => {
    const user = userEvent.setup();
    const fetchMock = mockFetchRoutes({
      '/api/admin/recipients': recipientsPage,
      '/api/admin/jobs/enqueue': jobTriggerResponse,
    });
    vi.stubGlobal('fetch', fetchMock);

    renderWithRouter(<Admin />);

    const enqueueButton = screen.getByRole('button', {
      name: 'Disparar enfileiramento',
    });

    await user.click(enqueueButton);

    await waitFor(() => {
      expect(
        screen.getByText(/Execution id: 123/),
      ).toBeInTheDocument();
    });

    const enqueueCallIndex = fetchMock.mock.calls.findIndex((call) =>
      String(call[0]).includes('/api/admin/jobs/enqueue'),
    );
    const request = expectRequest(fetchMock, enqueueCallIndex);
    expect(request.method).toBe('POST');
  });

  it('disables both job buttons while enqueue is in flight', async () => {
    const user = userEvent.setup();
    let resolveEnqueue: (value: any) => void;
    const enqueuePromise = new Promise((resolve) => {
      resolveEnqueue = resolve;
    });

    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.includes('/api/admin/jobs/enqueue')) {
        return enqueuePromise;
      }
      return jsonResponse(recipientsPage);
    });

    vi.stubGlobal('fetch', fetchMock);

    renderWithRouter(<Admin />);

    await waitFor(() =>
      expect(screen.getByText('ana@example.com')).toBeInTheDocument(),
    );

    const enqueueButton = screen.getByRole('button', {
      name: 'Disparar enfileiramento',
    });
    const fallbackButton = screen.getByRole('button', {
      name: 'Disparar fallback',
    });

    await user.click(enqueueButton);

    expect(screen.getByRole('button', { name: 'Disparando…' })).toBeDisabled();
    expect(enqueueButton).toBeDisabled();
    expect(fallbackButton).toBeDisabled();

    resolveEnqueue!(jsonResponse(jobTriggerResponse));

    await waitFor(() => {
      expect(screen.queryByRole('button', { name: 'Disparando…' })).not.toBeInTheDocument();
    });
    expect(enqueueButton).not.toBeDisabled();
    expect(fallbackButton).not.toBeDisabled();
  });

  it('triggers fallback job', async () => {
    const user = userEvent.setup();
    const fetchMock = mockFetchRoutes({
      '/api/admin/recipients': recipientsPage,
      '/api/admin/jobs/fallback': jobTriggerResponse,
    });
    vi.stubGlobal('fetch', fetchMock);

    renderWithRouter(<Admin />);

    const fallbackButton = screen.getByRole('button', {
      name: 'Disparar fallback',
    });

    await user.click(fallbackButton);

    await waitFor(() => {
      const fallbackCallIndex = fetchMock.mock.calls.findIndex((call) =>
        String(call[0]).includes('/api/admin/jobs/fallback'),
      );
      const request = expectRequest(fetchMock, fallbackCallIndex);
      expect(request.method).toBe('POST');
    });
  });

  it('shows error on job failure', async () => {
    const user = userEvent.setup();
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.includes('/api/admin/jobs/enqueue')) {
        return jsonResponse({}, { status: 400, ok: false });
      }
      return jsonResponse(recipientsPage);
    });

    vi.stubGlobal('fetch', fetchMock);

    renderWithRouter(<Admin />);

    await user.click(
      screen.getByRole('button', { name: 'Disparar enfileiramento' }),
    );

    await waitFor(() => {
      expect(screen.getByText(/status 400/)).toBeInTheDocument();
    });
  });

  it('clicks Reprocessar and shows success message', async () => {
    const user = userEvent.setup();
    const fetchMock = mockFetchRoutes({
      '/api/admin/recipients': recipientsPage,
      '/api/admin/recipients/9/retry': retryResponse,
    });
    vi.stubGlobal('fetch', fetchMock);

    renderWithRouter(<Admin />);

    await waitFor(() =>
      expect(screen.getByText('ana@example.com')).toBeInTheDocument(),
    );

    const retryButton = screen.getByRole('button', { name: 'Reprocessar' });
    await user.click(retryButton);

    await waitFor(() => {
      expect(
        screen.getByText(/Destinatário 9 marcado para reprocessamento/),
      ).toBeInTheDocument();
    });

    const retryCallIndex = fetchMock.mock.calls.findIndex((call) =>
      String(call[0]).includes('/api/admin/recipients/9/retry'),
    );
    const request = expectRequest(fetchMock, retryCallIndex);
    expect(request.method).toBe('POST');
  });

  it('refetches recipients list after retry', async () => {
    const user = userEvent.setup();
    let retryCallCount = 0;
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.includes('/api/admin/recipients/9/retry')) {
        retryCallCount++;
        return jsonResponse(retryResponse);
      }
      return jsonResponse(recipientsPage);
    });

    vi.stubGlobal('fetch', fetchMock);

    renderWithRouter(<Admin />);

    await waitFor(() =>
      expect(screen.getByText('ana@example.com')).toBeInTheDocument(),
    );

    const initialRecipientsFetches = fetchMock.mock.calls.filter((call) =>
      String(call[0]).includes('/api/admin/recipients?'),
    ).length;

    const retryButton = screen.getByRole('button', { name: 'Reprocessar' });
    await user.click(retryButton);

    await waitFor(() => {
      const finalRecipientsFetches = fetchMock.mock.calls.filter((call) =>
        String(call[0]).includes('/api/admin/recipients?'),
      ).length;
      expect(finalRecipientsFetches).toBeGreaterThan(initialRecipientsFetches);
    });
  });

  it('filters by email', async () => {
    const user = userEvent.setup();
    const fetchMock = vi.fn(async () => {
      return jsonResponse(recipientsPage);
    });
    vi.stubGlobal('fetch', fetchMock);

    renderWithRouter(<Admin />);

    await waitFor(() =>
      expect(screen.getByText('ana@example.com')).toBeInTheDocument(),
    );

    const emailInput = screen.getByLabelText('Buscar por e-mail');
    await user.type(emailInput, 'test@example.com');
    await user.click(screen.getByRole('button', { name: 'Buscar' }));

    await waitFor(() => {
      const request = expectRequest(
        fetchMock,
        fetchMock.mock.calls.length - 1,
      );
      expect(request.url).toContain('email=test%40example.com');
    });
  });

  it('filters by state', async () => {
    const user = userEvent.setup();
    const fetchMock = vi.fn(async () => {
      return jsonResponse(recipientsPage);
    });
    vi.stubGlobal('fetch', fetchMock);

    renderWithRouter(<Admin />);

    await waitFor(() =>
      expect(screen.getByText('ana@example.com')).toBeInTheDocument(),
    );

    const stateSelect = screen.getByLabelText('Filtrar por estado');
    await user.selectOptions(stateSelect, 'delivered');
    await user.click(screen.getByRole('button', { name: 'Buscar' }));

    await waitFor(() => {
      const request = expectRequest(
        fetchMock,
        fetchMock.mock.calls.length - 1,
      );
      expect(request.url).toContain('state=delivered');
    });
  });

  it('filters by both email and state', async () => {
    const user = userEvent.setup();
    const fetchMock = vi.fn(async () => {
      return jsonResponse(recipientsPage);
    });
    vi.stubGlobal('fetch', fetchMock);

    renderWithRouter(<Admin />);

    await waitFor(() =>
      expect(screen.getByText('ana@example.com')).toBeInTheDocument(),
    );

    const emailInput = screen.getByLabelText('Buscar por e-mail');
    const stateSelect = screen.getByLabelText('Filtrar por estado');

    await user.type(emailInput, 'test@example.com');
    await user.selectOptions(stateSelect, 'inFlight');
    await user.click(screen.getByRole('button', { name: 'Buscar' }));

    await waitFor(() => {
      const request = expectRequest(
        fetchMock,
        fetchMock.mock.calls.length - 1,
      );
      expect(request.url).toContain('email=test%40example.com');
      expect(request.url).toContain('state=inFlight');
    });
  });

  it('separates a failing recipient from one merely in flight, and shows the count', async () => {
    // Both read processed=true, sent=false. The attempt count is the only thing that tells them
    // apart, which is why it has to reach the list at all.
    const fetchMock = mockFetchRoutes({
      '/api/admin/recipients': {
        ...recipientsPage,
        total: 2,
        items: [
          { ...recipientsPage.items[0], id: 1, email: 'queued@example.com', processed: true, sent: false, attempts: 0 },
          { ...recipientsPage.items[0], id: 2, email: 'stuck@example.com', processed: true, sent: false, attempts: 4 },
        ],
      },
    });
    vi.stubGlobal('fetch', fetchMock);

    renderWithRouter(<Admin />);

    await waitFor(() =>
      expect(screen.getByText('stuck@example.com')).toBeInTheDocument(),
    );
    // By role, not by text: the dropdown carries the same words as the badges.
    expect(screen.getAllByRole('status').map((badge) => badge.textContent))
      .toEqual(['Em trânsito', 'Falhando (4)']);
  });

  it('offers the failing state as a filter, so the dashboard count can be opened', async () => {
    const user = userEvent.setup();
    const fetchMock = vi.fn(async () => jsonResponse(recipientsPage));
    vi.stubGlobal('fetch', fetchMock);

    renderWithRouter(<Admin />);

    await waitFor(() =>
      expect(screen.getByText('ana@example.com')).toBeInTheDocument(),
    );

    await user.selectOptions(screen.getByLabelText('Filtrar por estado'), 'failing');
    await user.click(screen.getByRole('button', { name: 'Buscar' }));

    await waitFor(() => {
      const request = expectRequest(fetchMock, fetchMock.mock.calls.length - 1);
      expect(request.url).toContain('state=failing');
    });
  });

  it('resets page to 0 when filtering', async () => {
    const user = userEvent.setup();
    const fetchMock = vi.fn(async () => jsonResponse(recipientsPage));
    vi.stubGlobal('fetch', fetchMock);

    renderWithRouter(<Admin />);

    await waitFor(() =>
      expect(screen.getByText('ana@example.com')).toBeInTheDocument(),
    );

    // Navigate to page 1
    await user.click(screen.getByRole('button', { name: 'Próxima' }));

    // Now filter
    const emailInput = screen.getByLabelText('Buscar por e-mail');
    await user.type(emailInput, 'test@example.com');
    await user.click(screen.getByRole('button', { name: 'Buscar' }));

    await waitFor(() => {
      const request = expectRequest(
        fetchMock,
        fetchMock.mock.calls.length - 1,
      );
      expect(request.url).toContain('page=0');
    });
  });

  it('shows badge as Entregue when sent=true', async () => {
    const deliveredData = {
      page: 0,
      size: 20,
      total: 1,
      items: [
        {
          id: 1,
          email: 'delivered@example.com',
          messageId: 1,
          processed: true,
          sent: true,
          attempts: 0,
          createdAt: '2026-08-14T10:00:00Z',
        },
      ],
    };
    const fetchMock = mockFetchRoutes({
      '/api/admin/recipients': deliveredData,
    });
    vi.stubGlobal('fetch', fetchMock);

    renderWithRouter(<Admin />);

    // Wait for the email to appear (which means the recipient data loaded)
    await waitFor(() => {
      expect(screen.getByText('delivered@example.com')).toBeInTheDocument();
    });

    // Verify badge is rendered with status role
    const badge = screen.getByRole('status');
    expect(badge.textContent).toBe('Entregue');
  });

  it('shows badge as Em trânsito when processed=true but sent=false and attempts=0', async () => {
    const inFlightData = {
      page: 0,
      size: 20,
      total: 1,
      items: [
        {
          id: 1,
          email: 'inflight@example.com',
          messageId: 1,
          processed: true,
          sent: false,
          attempts: 0,
          createdAt: '2026-08-14T10:00:00Z',
        },
      ],
    };
    const fetchMock = mockFetchRoutes({
      '/api/admin/recipients': inFlightData,
    });
    vi.stubGlobal('fetch', fetchMock);

    renderWithRouter(<Admin />);

    // Wait for the email to appear
    await waitFor(() => {
      expect(screen.getByText('inflight@example.com')).toBeInTheDocument();
    });

    // Verify badge is rendered with status role
    const badge = screen.getByRole('status');
    expect(badge.textContent).toBe('Em trânsito');
  });

  it('shows badge as Pendente when processed=false', async () => {
    const pendingData = {
      page: 0,
      size: 20,
      total: 1,
      items: [
        {
          id: 1,
          email: 'pending@example.com',
          messageId: 1,
          processed: false,
          sent: false,
          attempts: 0,
          createdAt: '2026-08-14T10:00:00Z',
        },
      ],
    };
    const fetchMock = mockFetchRoutes({
      '/api/admin/recipients': pendingData,
    });
    vi.stubGlobal('fetch', fetchMock);

    renderWithRouter(<Admin />);

    // Wait for the email to appear
    await waitFor(() => {
      expect(screen.getByText('pending@example.com')).toBeInTheDocument();
    });

    // Verify badge is rendered with status role
    const badge = screen.getByRole('status');
    expect(badge.textContent).toBe('Pendente');
  });

  it('shows badge as Falhando when processed=true, sent=false, and attempts>0', async () => {
    const failingData = {
      page: 0,
      size: 20,
      total: 1,
      items: [
        {
          id: 1,
          email: 'failing@example.com',
          messageId: 1,
          processed: true,
          sent: false,
          attempts: 3,
          createdAt: '2026-08-14T10:00:00Z',
        },
      ],
    };
    const fetchMock = mockFetchRoutes({
      '/api/admin/recipients': failingData,
    });
    vi.stubGlobal('fetch', fetchMock);

    renderWithRouter(<Admin />);

    // Wait for the email to appear
    await waitFor(() => {
      expect(screen.getByText('failing@example.com')).toBeInTheDocument();
    });

    // Verify badge is rendered with status role and shows the attempt count
    const badge = screen.getByRole('status');
    expect(badge.textContent).toBe('Falhando (3)');
  });
});
