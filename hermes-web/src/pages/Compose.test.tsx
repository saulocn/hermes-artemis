import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import Compose from './Compose';
import { renderWithRouter, mockFetchRoutes, expectRequest, jsonResponse } from '../test/helpers';
import { createMessageResponse } from '../test/fixtures';

describe('Compose page', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('renders the compose form fields', () => {
    vi.stubGlobal('fetch', mockFetchRoutes({}));
    renderWithRouter(<Compose />);

    expect(screen.getByLabelText('Título')).toBeInTheDocument();
    expect(screen.getByLabelText('Texto')).toBeInTheDocument();
    expect(screen.getByLabelText('Tipo de conteúdo')).toBeInTheDocument();
    expect(screen.getByLabelText(/Destinatários/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Enviar' })).toBeInTheDocument();
  });

  it('shows validation errors when submitting an empty form', async () => {
    const user = userEvent.setup();
    vi.stubGlobal('fetch', mockFetchRoutes({}));
    renderWithRouter(<Compose />);

    await user.click(screen.getByRole('button', { name: 'Enviar' }));

    expect(await screen.findByText('Informe um título.')).toBeInTheDocument();
    expect(screen.getByText('Informe o texto da mensagem.')).toBeInTheDocument();
    expect(screen.getByText('Informe ao menos um destinatário.')).toBeInTheDocument();
  });

  it('fills form, submits, and shows success banner with correct request body', async () => {
    const user = userEvent.setup();
    const fetchMock = mockFetchRoutes({ '/api/message': createMessageResponse });
    vi.stubGlobal('fetch', fetchMock);
    renderWithRouter(<Compose />);

    const titleInput = screen.getByLabelText('Título');
    const textInput = screen.getByLabelText('Texto');
    const contentTypeSelect = screen.getByLabelText('Tipo de conteúdo');
    const recipientsInput = screen.getByLabelText(/Destinatários/);

    await user.type(titleInput, 'Test Campaign');
    await user.type(textInput, 'This is the message body.');
    await user.selectOptions(contentTypeSelect, 'text/html');
    await user.type(recipientsInput, 'alice@example.com\nbob@example.com');

    await user.click(screen.getByRole('button', { name: 'Enviar' }));

    await waitFor(() => {
      expect(screen.getByText(/Mensagem criada com sucesso/)).toBeInTheDocument();
    });

    const request = expectRequest(fetchMock, 0);
    expect(request.url).toContain('/api/message');
    expect(request.method).toBe('POST');
    expect(request.body).toEqual({
      title: 'Test Campaign',
      text: 'This is the message body.',
      contentType: 'text/html',
      recipients: ['alice@example.com', 'bob@example.com'],
    });

    // Success message text may be split across nodes, use regex to find it
    expect(screen.getByText(/Id:.*42/)).toBeInTheDocument();
  });

  it('parses comma-separated and newline-separated recipients', async () => {
    const user = userEvent.setup();
    const fetchMock = mockFetchRoutes({ '/api/message': createMessageResponse });
    vi.stubGlobal('fetch', fetchMock);
    renderWithRouter(<Compose />);

    await user.type(screen.getByLabelText('Título'), 'Test');
    await user.type(screen.getByLabelText('Texto'), 'Body');
    await user.type(
      screen.getByLabelText(/Destinatários/),
      'alice@example.com, bob@example.com\ncharlie@example.com,\n,david@example.com',
    );

    await user.click(screen.getByRole('button', { name: 'Enviar' }));

    await waitFor(() => {
      const request = expectRequest(fetchMock, 0);
      expect(request.body.recipients).toEqual([
        'alice@example.com',
        'bob@example.com',
        'charlie@example.com',
        'david@example.com',
      ]);
    });
  });

  it('shows invalid email error for malformed addresses', async () => {
    const user = userEvent.setup();
    vi.stubGlobal('fetch', mockFetchRoutes({}));
    renderWithRouter(<Compose />);

    await user.type(screen.getByLabelText('Título'), 'Test');
    await user.type(screen.getByLabelText('Texto'), 'Body');
    await user.type(screen.getByLabelText(/Destinatários/), 'alice@example.com\ninvalid-email\nbob@example.com');

    await user.click(screen.getByRole('button', { name: 'Enviar' }));

    expect(
      await screen.findByText(/Endereços inválidos: invalid-email/),
    ).toBeInTheDocument();
  });

  it('shows loading state and disables button while submitting', async () => {
    const user = userEvent.setup();
    let resolveResponse: (value: any) => void;
    const responsePromise = new Promise((resolve) => {
      resolveResponse = resolve;
    });
    const fetchMock = vi.fn(async () => responsePromise);
    vi.stubGlobal('fetch', fetchMock);
    renderWithRouter(<Compose />);

    const titleInput = screen.getByLabelText('Título');
    const textInput = screen.getByLabelText('Texto');
    const recipientsInput = screen.getByLabelText(/Destinatários/);

    await user.type(titleInput, 'Test');
    await user.type(textInput, 'Body');
    await user.type(recipientsInput, 'alice@example.com');

    const submitButton = screen.getByRole('button', { name: 'Enviar' });
    await user.click(submitButton);

    expect(screen.getByRole('button', { name: 'Enviando…' })).toBeDisabled();

    resolveResponse!(jsonResponse(createMessageResponse));

    await waitFor(() => {
      expect(screen.getByText(/Mensagem criada com sucesso/)).toBeInTheDocument();
    });
  });

  it('clears title and text after success, but preserves contentType', async () => {
    const user = userEvent.setup();
    const fetchMock = mockFetchRoutes({ '/api/message': createMessageResponse });
    vi.stubGlobal('fetch', fetchMock);
    renderWithRouter(<Compose />);

    const titleInput = screen.getByLabelText('Título') as HTMLInputElement;
    const textInput = screen.getByLabelText('Texto') as HTMLTextAreaElement;
    const contentTypeSelect = screen.getByLabelText('Tipo de conteúdo') as HTMLSelectElement;
    const recipientsInput = screen.getByLabelText(/Destinatários/);

    await user.type(titleInput, 'Test');
    await user.type(textInput, 'Body');
    await user.selectOptions(contentTypeSelect, 'text/html');
    await user.type(recipientsInput, 'alice@example.com');

    await user.click(screen.getByRole('button', { name: 'Enviar' }));

    await waitFor(() => {
      expect(screen.getByText(/Mensagem criada com sucesso/)).toBeInTheDocument();
    });

    expect(titleInput.value).toBe('');
    expect(textInput.value).toBe('');
    expect(recipientsInput).toHaveValue('');
    expect(contentTypeSelect.value).toBe('text/html');
  });

  it('shows error banner and preserves form on API failure', async () => {
    const user = userEvent.setup();
    const fetchMock = vi.fn(async () =>
      jsonResponse({}, { status: 500, ok: false }),
    );
    vi.stubGlobal('fetch', fetchMock);
    renderWithRouter(<Compose />);

    const titleInput = screen.getByLabelText('Título') as HTMLInputElement;
    const textInput = screen.getByLabelText('Texto') as HTMLTextAreaElement;
    const recipientsInput = screen.getByLabelText(/Destinatários/) as HTMLTextAreaElement;

    await user.type(titleInput, 'Test');
    await user.type(textInput, 'Body');
    await user.type(recipientsInput, 'alice@example.com');

    await user.click(screen.getByRole('button', { name: 'Enviar' }));

    await waitFor(() => {
      expect(screen.getByText(/status 500/)).toBeInTheDocument();
    });

    expect(titleInput.value).toBe('Test');
    expect(textInput.value).toBe('Body');
    expect(recipientsInput.value).toBe('alice@example.com');
  });
});
