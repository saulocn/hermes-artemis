import {
  AdminStats,
  BrokerStatus,
  CreateMessageResponse,
  JobTriggerResponse,
  MessagesPage,
  RecipientsPage,
  RetryResponse,
  ThroughputResponse,
} from '../api/client';

export const adminStats: AdminStats = {
  pending: 3,
  inFlight: 2,
  delivered: 10,
  totalMessages: 15,
  oldestPendingSeconds: 42,
};

export const brokerStatus: BrokerStatus = {
  kind: 'artemis',
  queueDepth: 5,
  dlqDepth: 0,
  error: null,
};

export const throughput: ThroughputResponse = {
  points: [
    { minute: '2026-08-14T10:00:00Z', delivered: 4 },
    { minute: '2026-08-14T10:01:00Z', delivered: 5 },
  ],
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
