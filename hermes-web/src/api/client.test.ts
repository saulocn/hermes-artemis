import { afterEach, describe, expect, it, vi } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import {
  ApiError,
  createMessage,
  getAdminStats,
  getBrokerStatus,
  getMessages,
  getRates,
  getRecipients,
  getThroughput,
  retryRecipient,
  triggerJob,
  RECIPIENT_STATES,
  JOB_NAMES,
} from './client';

function jsonResponse(body: unknown, init: { status?: number; ok?: boolean } = {}) {
  const status = init.status ?? 200;
  return {
    ok: init.ok ?? (status >= 200 && status < 300),
    status,
    statusText: 'status text',
    json: async () => body,
    text: async () => JSON.stringify(body),
  } as Response;
}

describe('api/client', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('createMessage posts to /api/message and returns the parsed body', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({ id: 1, title: 't', text: 'x', contentType: 'text/plain' }),
    );
    vi.stubGlobal('fetch', fetchMock);

    const result = await createMessage({
      title: 't',
      text: 'x',
      contentType: 'text/plain',
      recipients: ['a@example.com'],
    });

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/message',
      expect.objectContaining({ method: 'POST' }),
    );
    expect(result).toEqual({ id: 1, title: 't', text: 'x', contentType: 'text/plain' });
  });

  it('getAdminStats fetches /api/admin/stats', async () => {
    const stats = {
      pending: 1,
      inFlight: 2,
      delivered: 3,
      totalMessages: 4,
      oldestPendingSeconds: 5,
    };
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(stats));
    vi.stubGlobal('fetch', fetchMock);

    const result = await getAdminStats();

    expect(fetchMock).toHaveBeenCalledWith('/api/admin/stats', expect.any(Object));
    expect(result).toEqual(stats);
  });

  it('getThroughput passes the minutes query param', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ points: [] }));
    vi.stubGlobal('fetch', fetchMock);

    await getThroughput(30);

    expect(fetchMock).toHaveBeenCalledWith('/api/admin/throughput?minutes=30', expect.any(Object));
  });

  it('getBrokerStatus handles a null queueDepth/dlqDepth with an error message', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({ kind: 'artemis', queueDepth: null, dlqDepth: null, error: 'broker unreachable' }),
    );
    vi.stubGlobal('fetch', fetchMock);

    const result = await getBrokerStatus();

    expect(result.queueDepth).toBeNull();
    expect(result.dlqDepth).toBeNull();
    expect(result.error).toBe('broker unreachable');
  });

  it('getMessages builds the query string with page/size/q', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ page: 0, size: 20, total: 0, items: [] }));
    vi.stubGlobal('fetch', fetchMock);

    await getMessages({ page: 1, size: 10, q: 'hello' });

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/admin/messages?page=1&size=10&q=hello',
      expect.any(Object),
    );
  });

  it('getRecipients omits state when not provided', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ page: 0, size: 20, total: 0, items: [] }));
    vi.stubGlobal('fetch', fetchMock);

    await getRecipients({ email: 'a@example.com' });

    const calledUrl = fetchMock.mock.calls[0][0] as string;
    expect(calledUrl).toContain('email=a%40example.com');
    expect(calledUrl).not.toContain('state=');
  });

  it('retryRecipient posts to the retry endpoint', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ id: 7, retried: true }));
    vi.stubGlobal('fetch', fetchMock);

    const result = await retryRecipient(7);

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/admin/recipients/7/retry',
      expect.objectContaining({ method: 'POST' }),
    );
    expect(result).toEqual({ id: 7, retried: true });
  });

  it('triggerJob posts to the job path and reports the execution id', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ executionId: 42 }));
    vi.stubGlobal('fetch', fetchMock);

    expect(await triggerJob('enqueue')).toEqual({ started: true, executionId: 42 });
    expect(await triggerJob('fallback')).toEqual({ started: true, executionId: 42 });
    expect(fetchMock).toHaveBeenNthCalledWith(
      1,
      '/api/admin/jobs/enqueue',
      expect.objectContaining({ method: 'POST' }),
    );
    expect(fetchMock).toHaveBeenNthCalledWith(
      2,
      '/api/admin/jobs/fallback',
      expect.objectContaining({ method: 'POST' }),
    );
  });

  it('reports a 409 as a refused trigger rather than throwing', async () => {
    // The server answers 409 when a run is already in flight. That is an outcome the screen
    // should state plainly, not an ApiError it renders as a failure.
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse({ error: 'job already running' }, { status: 409 })),
    );

    expect(await triggerJob('enqueue')).toEqual({ started: false, reason: 'already-running' });
  });

  it('still throws for other job failures', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({}, { status: 500 })));

    await expect(triggerJob('enqueue')).rejects.toBeInstanceOf(ApiError);
  });

  it('throws an ApiError carrying the status on a non-2xx response', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({ message: 'boom' }, { status: 500, ok: false }),
    );
    vi.stubGlobal('fetch', fetchMock);

    await expect(getAdminStats()).rejects.toBeInstanceOf(ApiError);
    await expect(getAdminStats()).rejects.toMatchObject({ status: 500 });
  });

  it('throws an ApiError on a 404 response', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({}, { status: 404, ok: false }),
    );
    vi.stubGlobal('fetch', fetchMock);

    await expect(retryRecipient(999)).rejects.toMatchObject({ status: 404 });
  });

  describe('absent fields from the server', () => {
    // JSON-B omits nulls, so an idle pipeline produces objects with keys simply missing. Every
    // fixture in this suite was written with all fields present, which is why 175 green tests
    // still shipped a dashboard that went blank on a real, idle server.
    it('reads a rates response with span, sustained and lastPublishAt absent', async () => {
      const idle = {
        created: { count: 0, window: 30, ratePerSecond: 0 },
        published: { count: 0, window: 30, ratePerSecond: 0 },
        claimed: { count: 0, window: 30, ratePerSecond: 0 },
        asOf: '2026-08-17T14:06:59',
      };
      vi.stubGlobal('fetch', vi.fn(async () => jsonResponse(idle)));

      const rates = await getRates(30);

      expect(rates.created.span).toBeNull();
      expect(rates.created.sustainedPerSecond).toBeNull();
      expect(rates.lastPublishAt).toBeNull();
    });

    it('reads a broker status with ackRate and depths absent', async () => {
      vi.stubGlobal('fetch', vi.fn(async () => jsonResponse({ kind: 'artemis' })));

      const status = await getBrokerStatus();

      expect(status.ackRate).toBeNull();
      expect(status.queueDepth).toBeNull();
      expect(status.error).toBeNull();
    });

    it('reads stats with oldestPendingSeconds absent', async () => {
      vi.stubGlobal('fetch', vi.fn(async () => jsonResponse({
        pending: 0, inFlight: 0, failing: 0, delivered: 0, totalMessages: 0,
      })));

      expect((await getAdminStats()).oldestPendingSeconds).toBeNull();
    });
  });

  describe('contract validation', () => {
    // These tests ensure the code and the server stay in sync by reading a shared contract file.
    // When commit e1da578 shipped, `failing` existed in the dashboard count but not in the filter,
    // because the list of states was repeated in three places and diverged in two of them. The
    // symptom was a screen showing "10 recipients failing" with no way to view them.

    it('verifies RECIPIENT_STATES values match contracts/recipient-states.json', () => {
      const contractPath = resolve(__dirname, '../../../contracts/recipient-states.json');
      const contractContent = readFileSync(contractPath, 'utf-8');
      const expectedStates: string[] = JSON.parse(contractContent);

      const actualStates = RECIPIENT_STATES.map((state) => state.value);

      expect(actualStates).toEqual(expectedStates);
    });

    // Same logic as recipient states: the two job names are used to build URLs on both client
    // and server. If they diverge, triggerJob might send the client to a 404 or the UI might
    // render an option the server doesn't accept.
    it('verifies JOB_NAMES values match contracts/jobs.json', () => {
      const contractPath = resolve(__dirname, '../../../contracts/jobs.json');
      const contractContent = readFileSync(contractPath, 'utf-8');
      const expectedJobs: string[] = JSON.parse(contractContent);

      const actualJobs = Array.from(JOB_NAMES);

      expect(actualJobs).toEqual(expectedJobs);
    });
  });
});
