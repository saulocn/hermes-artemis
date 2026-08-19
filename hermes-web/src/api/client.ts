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
  /** Published, undelivered, and known to have thrown at least once. A partition alongside pending, inFlight, and delivered. */
  failing: number;
  delivered: number;
  totalMessages: number;
  oldestPendingSeconds: number | null;
}

export async function getAdminStats(): Promise<AdminStats> {
  // oldestPendingSeconds is absent, not null, when nothing is pending. See orNull below.
  const raw = await request<AdminStats>('/admin/stats');
  return { ...raw, oldestPendingSeconds: raw.oldestPendingSeconds ?? null };
}

// ---- Admin: rates (per-stage rates) -----------------------------------------------

export interface StageRate {
  count: number;
  window: number;
  span: number | null;
  ratePerSecond: number;
  sustainedPerSecond: number | null;
}

export interface RatesResponse {
  created: StageRate;
  published: StageRate;
  claimed: StageRate;
  lastPublishAt: string | null;
  asOf: string;
}

/**
 * Absent is the same as null, and that translation happens here, once.
 *
 * JSON-B omits null fields entirely, so the server sends `{count, window, ratePerSecond}` with no
 * `span` and no `sustainedPerSecond` rather than sending them as null. The types below say
 * `number | null`, every consumer checked `!== null`, and `undefined !== null` is true — so the
 * dashboard called `.toFixed()` on undefined and the whole page went blank. Unit tests could not
 * catch it: the fixtures were written with every field present, which is a shape the server never
 * actually produces for an idle pipeline.
 *
 * Normalising at the boundary keeps that quirk in one function instead of turning every downstream
 * null check into a loose one, and lets the declared types be true inside the app.
 */
function orNull<T>(value: T | null | undefined): T | null {
  return value ?? null;
}

function normaliseStage(stage: StageRate): StageRate {
  return { ...stage, span: orNull(stage.span), sustainedPerSecond: orNull(stage.sustainedPerSecond) };
}

export async function getRates(window = 30): Promise<RatesResponse> {
  const raw = await request<RatesResponse>(`/admin/rates?window=${encodeURIComponent(window)}`);
  return {
    ...raw,
    created: normaliseStage(raw.created),
    published: normaliseStage(raw.published),
    claimed: normaliseStage(raw.claimed),
    lastPublishAt: orNull(raw.lastPublishAt),
  };
}

// ---- Admin: throughput (per-minute series) ------------------------------------

export interface ThroughputPoint {
  minute: string;
  count: number | null;
}

export interface ThroughputSeries {
  stage: string;
  points: ThroughputPoint[];
}

export interface ThroughputResponse {
  series: ThroughputSeries[];
  asOf: string;
}

export function getThroughput(minutes = 60): Promise<ThroughputResponse> {
  return request<ThroughputResponse>(`/admin/throughput?minutes=${encodeURIComponent(minutes)}`);
}

// ---- Admin: broker -----------------------------------------------------

export interface BrokerStatus {
  kind: 'artemis' | 'rabbit';
  queueDepth: number | null;
  dlqDepth: number | null;
  ackRate: number | null;
  error: string | null;
}

export async function getBrokerStatus(): Promise<BrokerStatus> {
  // queueDepth, dlqDepth, ackRate and error are all omitted when null — see orNull above.
  const raw = await request<BrokerStatus>('/admin/broker');
  return {
    ...raw,
    queueDepth: raw.queueDepth ?? null,
    dlqDepth: raw.dlqDepth ?? null,
    ackRate: raw.ackRate ?? null,
    error: raw.error ?? null,
  };
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

/**
 * The states a recipient row can be read as, named once.
 *
 * The server owns the same list in `RecipientState`; these are the wire names it accepts. Keeping
 * the label next to the value is what stops a state from existing in the filter but not the
 * legend, or the other way round — `failing` shipped as a dashboard count with no filter arm and
 * no option in the dropdown, because those lived in three separate lists.
 */
export const RECIPIENT_STATES = [
  { value: 'pending', label: 'Pendente' },
  { value: 'inFlight', label: 'Em trânsito' },
  { value: 'failing', label: 'Falhando' },
  { value: 'delivered', label: 'Entregue' },
] as const;

export type RecipientState = (typeof RECIPIENT_STATES)[number]['value'];

export interface Recipient {
  id: number;
  email: string;
  messageId: number;
  processed: boolean;
  sent: boolean;
  /** How many sends threw for this row. Zero means "never failed", not "never tried". */
  attempts: number;
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

/** The two jobs, named once. The server derives its own paths from the same two words. */
export type JobName = 'enqueue' | 'fallback';

/**
 * A refused trigger is an outcome, not a failure.
 *
 * The server answers 409 when a run of the same job is already in flight. Letting that surface
 * as a thrown ApiError put a red banner with a raw JSON body in front of the operator, which is
 * the opposite of what a "someone already started it" answer should look like.
 */
export type JobTriggerResult =
  | { started: true; executionId: number }
  | { started: false; reason: 'already-running' };

export async function triggerJob(job: JobName): Promise<JobTriggerResult> {
  try {
    const response = await request<JobTriggerResponse>(`/admin/jobs/${job}`, { method: 'POST' });
    return { started: true, executionId: response.executionId };
  } catch (error) {
    if (error instanceof ApiError && error.status === 409) {
      return { started: false, reason: 'already-running' };
    }
    throw error;
  }
}
