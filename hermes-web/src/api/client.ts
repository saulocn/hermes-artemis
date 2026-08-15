// Typed wrapper around the Hermes API. All requests go to same-origin
// `/api/...` — nginx proxies this to the hermes-api service, so there is
// no CORS handling and no base URL to configure.

export class ApiError extends Error {
  status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`/api${path}`, {
    headers: { 'Content-Type': 'application/json' },
    ...init,
  });

  if (!response.ok) {
    let detail = '';
    try {
      detail = await response.text();
    } catch {
      // ignore body read failures, fall back to status text only
    }
    const message = detail
      ? `Request to ${path} failed with status ${response.status}: ${detail}`
      : `Request to ${path} failed with status ${response.status} ${response.statusText}`;
    throw new ApiError(response.status, message);
  }

  return (await response.json()) as T;
}

// ---- Message creation -----------------------------------------------------

export interface CreateMessageRequest {
  title: string;
  text: string;
  contentType: string;
  recipients: string[];
}

export interface CreateMessageResponse {
  id: number;
  title: string;
  text: string;
  contentType: string;
}

export function createMessage(body: CreateMessageRequest): Promise<CreateMessageResponse> {
  return request<CreateMessageResponse>('/message', {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

// ---- Admin: stats -----------------------------------------------------

export interface AdminStats {
  pending: number;
  inFlight: number;
  delivered: number;
  totalMessages: number;
  oldestPendingSeconds: number | null;
}

export function getAdminStats(): Promise<AdminStats> {
  return request<AdminStats>('/admin/stats');
}

// ---- Admin: throughput -----------------------------------------------------

export interface ThroughputPoint {
  minute: string;
  delivered: number;
}

export interface ThroughputResponse {
  points: ThroughputPoint[];
}

export function getThroughput(minutes = 60): Promise<ThroughputResponse> {
  return request<ThroughputResponse>(`/admin/throughput?minutes=${encodeURIComponent(minutes)}`);
}

// ---- Admin: broker -----------------------------------------------------

export interface BrokerStatus {
  kind: 'artemis' | 'rabbit';
  queueDepth: number | null;
  dlqDepth: number | null;
  error: string | null;
}

export function getBrokerStatus(): Promise<BrokerStatus> {
  return request<BrokerStatus>('/admin/broker');
}

// ---- Admin: messages -----------------------------------------------------

export interface MessageSummary {
  id: number;
  title: string;
  contentType: string;
  createdAt: string;
  recipientCount: number;
  sentCount: number;
}

export interface MessagesPage {
  page: number;
  size: number;
  total: number;
  items: MessageSummary[];
}

export function getMessages(params: { page?: number; size?: number; q?: string } = {}): Promise<MessagesPage> {
  const search = new URLSearchParams();
  search.set('page', String(params.page ?? 0));
  search.set('size', String(params.size ?? 20));
  if (params.q) search.set('q', params.q);
  return request<MessagesPage>(`/admin/messages?${search.toString()}`);
}

// ---- Admin: recipients -----------------------------------------------------

export type RecipientState = 'pending' | 'inFlight' | 'delivered';

export interface Recipient {
  id: number;
  email: string;
  messageId: number;
  processed: boolean;
  sent: boolean;
  createdAt: string;
}

export interface RecipientsPage {
  page: number;
  size: number;
  total: number;
  items: Recipient[];
}

export function getRecipients(
  params: { email?: string; state?: RecipientState; page?: number; size?: number } = {},
): Promise<RecipientsPage> {
  const search = new URLSearchParams();
  search.set('page', String(params.page ?? 0));
  search.set('size', String(params.size ?? 20));
  if (params.email) search.set('email', params.email);
  if (params.state) search.set('state', params.state);
  return request<RecipientsPage>(`/admin/recipients?${search.toString()}`);
}

export interface RetryResponse {
  id: number;
  retried: boolean;
}

export function retryRecipient(id: number): Promise<RetryResponse> {
  return request<RetryResponse>(`/admin/recipients/${id}/retry`, { method: 'POST' });
}

// ---- Admin: jobs -----------------------------------------------------

export interface JobTriggerResponse {
  executionId: number;
}

export function triggerEnqueueJob(): Promise<JobTriggerResponse> {
  return request<JobTriggerResponse>('/admin/jobs/enqueue', { method: 'POST' });
}

export function triggerFallbackJob(): Promise<JobTriggerResponse> {
  return request<JobTriggerResponse>('/admin/jobs/fallback', { method: 'POST' });
}
