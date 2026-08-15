import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  ApiError,
  createMessage,
  getAdminStats,
  getBrokerStatus,
  getMessages,
  getRecipients,
  getThroughput,
  retryRecipient,
  triggerEnqueueJob,
  triggerFallbackJob,
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

  it('triggerEnqueueJob and triggerFallbackJob post and return executionId', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ executionId: 42 }));
    vi.stubGlobal('fetch', fetchMock);

    expect(await triggerEnqueueJob()).toEqual({ executionId: 42 });
    expect(await triggerFallbackJob()).toEqual({ executionId: 42 });
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
});
