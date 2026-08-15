import { render, RenderOptions } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import React from 'react';

/**
 * Render a component wrapped in MemoryRouter for testing routes.
 */
export function renderWithRouter(
  ui: React.ReactElement,
  options?: RenderOptions & { route?: string },
) {
  const { route = '/', ...renderOptions } = options || {};

  return render(<MemoryRouter initialEntries={[route]}>{ui}</MemoryRouter>, renderOptions);
}

/**
 * Create a mock Response object with JSON body.
 */
export function jsonResponse(
  body: unknown,
  init?: { status?: number; ok?: boolean },
): Response {
  const status = init?.status ?? 200;
  return {
    ok: init?.ok ?? (status >= 200 && status < 300),
    status,
    statusText: 'status text',
    json: async () => body,
    text: async () => JSON.stringify(body),
  } as Response;
}

/**
 * Install a fetch mock that routes requests by URL substring matching.
 * If no route matches, throws an Error naming the unexpected URL.
 */
/** A route may answer with a plain body, or with a body and a status. */
export type RouteResponse = unknown | { body: unknown; status: number };

function isStatusRoute(value: unknown): value is { body: unknown; status: number } {
  return typeof value === 'object' && value !== null && 'status' in value && 'body' in value;
}

export function mockFetchRoutes(routes: Record<string, RouteResponse>) {
  return vi.fn(async (input: RequestInfo | URL) => {
    const url = String(input);
    for (const [key, response] of Object.entries(routes)) {
      if (url.includes(key)) {
        return isStatusRoute(response)
          ? jsonResponse(response.body, { status: response.status })
          : jsonResponse(response);
      }
    }
    throw new Error(`unexpected url: ${url}`);
  });
}

/**
 * Get request details from a fetch mock.
 * Returns the URL, method, and parsed JSON body (if present) for the nth call.
 */
export function expectRequest(fetchMock: any, index: number) {
  const call = fetchMock.mock.calls[index];
  if (!call) {
    throw new Error(`No fetch call at index ${index}`);
  }
  const [url, init] = call;
  return {
    url: String(url),
    method: init?.method ?? 'GET',
    body: init?.body ? JSON.parse(String(init.body)) : undefined,
  };
}

// Ensure vi is available
import { vi } from 'vitest';
