import {
  AdminStats,
  BrokerStatus,
  CreateMessageResponse,
  JobTriggerResponse,
  MessagesPage,
  RatesResponse,
  RecipientsPage,
  RetryResponse,
  ThroughputResponse,
} from '../api/client';

export const adminStats: AdminStats = {
  pending: 3,
  failing: 0,
  inFlight: 2,
  delivered: 10,
  totalMessages: 15,
  oldestPendingSeconds: 42,
};

export const brokerStatus: BrokerStatus = {
  kind: 'artemis',
  queueDepth: 5,
  dlqDepth: 0,
  ackRate: 1.5,
  error: null,
};

export const throughput: ThroughputResponse = {
  series: [
    {
      stage: 'created_on',
      points: [
        { minute: '2026-08-14T10:00:00Z', count: 4 },
        { minute: '2026-08-14T10:01:00Z', count: 5 },
      ],
    },
    {
      stage: 'published_on',
      points: [
        { minute: '2026-08-14T10:00:00Z', count: 3 },
        { minute: '2026-08-14T10:01:00Z', count: 4 },
      ],
    },
    {
      stage: 'claimed_on',
      points: [
        { minute: '2026-08-14T10:00:00Z', count: 2 },
        { minute: '2026-08-14T10:01:00Z', count: 3 },
      ],
    },
  ],
  asOf: '2026-08-14T10:02:00Z',
};

export const messagesPage: MessagesPage = {
  page: 0,
  size: 20,
  total: 1,
  items: [
    {
      id: 1,
      title: 'Campanha de teste',
      contentType: 'text/plain',
      createdAt: '2026-08-14T10:00:00Z',
      recipientCount: 10,
      sentCount: 4,
    },
  ],
};

export const recipientsPage: RecipientsPage = {
  page: 0,
  size: 20,
  total: 1,
  items: [
    {
      id: 9,
      email: 'ana@example.com',
      messageId: 1,
      processed: false,
      sent: false,
      attempts: 0,
      createdAt: '2026-08-14T10:00:00Z',
    },
  ],
};

export const createMessageResponse: CreateMessageResponse = {
  id: 42,
  title: 'Test Message',
  text: 'This is a test message',
  contentType: 'text/plain',
};

export const jobTriggerResponse: JobTriggerResponse = {
  executionId: 123,
};

export const retryResponse: RetryResponse = {
  id: 9,
  retried: true,
};

export const rates: RatesResponse = {
  created: {
    count: 120,
    window: 30,
    span: 28,
    ratePerSecond: 4.0,
    sustainedPerSecond: 4.29,
  },
  published: {
    count: 90,
    window: 30,
    span: 5,
    ratePerSecond: 3.0,
    sustainedPerSecond: 18.0,
  },
  claimed: {
    count: 85,
    window: 30,
    span: 28,
    ratePerSecond: 2.83,
    sustainedPerSecond: 3.04,
  },
  lastPublishAt: '2026-08-14T10:02:05Z',
  asOf: '2026-08-14T10:02:30Z',
};
