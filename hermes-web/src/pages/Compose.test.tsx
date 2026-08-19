import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import Compose from './Compose';
import { renderWithRouter, mockFetchRoutes, expectRequest, jsonResponse } from '../test/helpers';
import { createMessageResponse } from '../test/fixtures';

vi.mock('../components/RichTextEditor', () => ({
  default: ({ value, onChange, ariaLabel }: { value: string; onChange: (html: string) => void; ariaLabel: string }) => (
    <textarea aria-label={ariaLabel} value={value} onChange={(e) => onChange(e.target.value)} />
  ),
}));

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
    const recipientsInput = screen.getByLabelText(/Destinatários/);

    await user.type(titleInput, 'Test Campaign');
    await user.type(textInput, 'This is the message body.');
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
      contentType: 'text/plain',
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
    await user.type(recipientsInput, 'alice@example.com');

    await user.click(screen.getByRole('button', { name: 'Enviar' }));

    await waitFor(() => {
      expect(screen.getByText(/Mensagem criada com sucesso/)).toBeInTheDocument();
    });

    expect(titleInput.value).toBe('');
    expect(textInput.value).toBe('');
    expect(recipientsInput).toHaveValue('');
    expect(contentTypeSelect.value).toBe('text/plain');
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

  it('switches to text/html and shows editor and preview', async () => {
    const user = userEvent.setup();
    vi.stubGlobal('fetch', mockFetchRoutes({}));
    renderWithRouter(<Compose />);

    const contentTypeSelect = screen.getByLabelText('Tipo de conteúdo');

    expect(screen.getByLabelText('Texto')).toBeInTheDocument();

    await user.selectOptions(contentTypeSelect, 'text/html');

    // After switching to HTML mode, the editor should appear (Suspense will resolve it)
    const editorTextarea = await screen.findByLabelText('Texto');
    expect(editorTextarea).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Copiar HTML' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Baixar .html' })).toBeInTheDocument();
  });

  it('submitting in html mode POSTs wrapped document with contentType text/html', async () => {
    const user = userEvent.setup();
    const fetchMock = mockFetchRoutes({ '/api/message': createMessageResponse });
    vi.stubGlobal('fetch', fetchMock);
    renderWithRouter(<Compose />);

    const titleInput = screen.getByLabelText('Título');
    const contentTypeSelect = screen.getByLabelText('Tipo de conteúdo');
    const recipientsInput = screen.getByLabelText(/Destinatários/);

    await user.type(titleInput, 'Newsletter');
    await user.selectOptions(contentTypeSelect, 'text/html');

    const editorTextarea = await screen.findByLabelText('Texto');
    await user.type(editorTextarea, '<p>Hello World</p>');
    await user.type(recipientsInput, 'alice@example.com');

    await user.click(screen.getByRole('button', { name: 'Enviar' }));

    await waitFor(() => {
      expect(screen.getByText(/Mensagem criada com sucesso/)).toBeInTheDocument();
    });

    const request = expectRequest(fetchMock, 0);
    expect(request.body.contentType).toBe('text/html');
    expect(request.body.text).toMatch(/^<!DOCTYPE/);
    expect(request.body.text).toContain('Hello World');
    expect(request.body.text).not.toContain('<script');
  });

  it('seeds html from plain text when switching plain -> html', async () => {
    const user = userEvent.setup();
    vi.stubGlobal('fetch', mockFetchRoutes({}));
    renderWithRouter(<Compose />);

    const plainTextarea = screen.getByLabelText('Texto');
    const contentTypeSelect = screen.getByLabelText('Tipo de conteúdo');

    await user.type(plainTextarea, 'This is plain text');

    await user.selectOptions(contentTypeSelect, 'text/html');

    const htmlTextarea = (await screen.findByLabelText('Texto')) as HTMLTextAreaElement;
    expect(htmlTextarea.value).toContain('This is plain text');
  });

  it('preserves both buffers when switching back and forth', async () => {
    const user = userEvent.setup();
    vi.stubGlobal('fetch', mockFetchRoutes({}));
    renderWithRouter(<Compose />);

    const contentTypeSelect = screen.getByLabelText('Tipo de conteúdo');

    // Type plain text
    let textInput = screen.getByLabelText('Texto') as HTMLTextAreaElement;
    await user.type(textInput, 'Plain text content');
    const plainValue = textInput.value;

    // Switch to HTML
    await user.selectOptions(contentTypeSelect, 'text/html');
    let htmlTextarea = (await screen.findByLabelText('Texto')) as HTMLTextAreaElement;
    await user.type(htmlTextarea, '<p>HTML content</p>');

    // Switch back to plain
    await user.selectOptions(contentTypeSelect, 'text/plain');
    textInput = screen.getByLabelText('Texto') as HTMLTextAreaElement;
    expect(textInput.value).toBe(plainValue);

    // Switch back to HTML, plain buffer should be preserved but html buffer should still be there
    await user.selectOptions(contentTypeSelect, 'text/html');
    htmlTextarea = (await screen.findByLabelText('Texto')) as HTMLTextAreaElement;
    expect(htmlTextarea.value).toContain('HTML content');
  });

  it('shows validation error for empty html content', async () => {
    const user = userEvent.setup();
    vi.stubGlobal('fetch', mockFetchRoutes({}));
    renderWithRouter(<Compose />);

    const titleInput = screen.getByLabelText('Título');
    const contentTypeSelect = screen.getByLabelText('Tipo de conteúdo');
    const recipientsInput = screen.getByLabelText(/Destinatários/);

    await user.type(titleInput, 'Test');
    await user.selectOptions(contentTypeSelect, 'text/html');

    const editorTextarea = await screen.findByLabelText('Texto');
    await user.type(editorTextarea, '<p><br></p>');
    await user.type(recipientsInput, 'alice@example.com');

    await user.click(screen.getByRole('button', { name: 'Enviar' }));

    expect(screen.getByText('Informe o texto da mensagem.')).toBeInTheDocument();
  });

  it('shows hint when switching back to plain with html content preserved', async () => {
    const user = userEvent.setup();
    vi.stubGlobal('fetch', mockFetchRoutes({}));
    renderWithRouter(<Compose />);

    const contentTypeSelect = screen.getByLabelText('Tipo de conteúdo');

    // Switch to HTML and type content
    await user.selectOptions(contentTypeSelect, 'text/html');
    const htmlTextarea = await screen.findByLabelText('Texto');
    await user.type(htmlTextarea, '<p>HTML content</p>');

    // Switch back to plain
    await user.selectOptions(contentTypeSelect, 'text/plain');

    expect(
      screen.getByText('O conteúdo HTML foi mantido e reaparece ao voltar para text/html.'),
    ).toBeInTheDocument();
  });
});
