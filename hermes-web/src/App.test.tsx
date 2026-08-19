import { screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import App from './App';
import { renderWithRouter, mockFetchRoutes } from './test/helpers';
import { adminStats, brokerStatus, throughput } from './test/fixtures';

describe('App routing', () => {
  it('renders Dashboard when navigating to /', async () => {
    const fetchMock = mockFetchRoutes({
      '/api/admin/stats': adminStats,
      '/api/admin/broker': brokerStatus,
      '/api/admin/throughput': throughput,
    });
    vi.stubGlobal('fetch', fetchMock);

    renderWithRouter(<App />, { route: '/' });

    expect(screen.getByRole('heading', { name: 'Painel' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Painel' })).toHaveAttribute(
      'aria-current',
      'page',
    );

    await waitFor(() => {
      expect(screen.getByText('Pendentes')).toBeInTheDocument();
    });
  });

  it('renders Compose when navigating to /compose', () => {
    const fetchMock = mockFetchRoutes({});
    vi.stubGlobal('fetch', fetchMock);

    renderWithRouter(<App />, { route: '/compose' });

    expect(screen.getByRole('heading', { name: 'Nova mensagem' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Nova mensagem' })).toHaveAttribute(
      'aria-current',
      'page',
    );
    expect(screen.getByLabelText('Título')).toBeInTheDocument();
  });

  it('renders History when navigating to /history', async () => {
    const fetchMock = mockFetchRoutes({
      '/api/admin/messages': { page: 0, size: 20, total: 0, items: [] },
    });
    vi.stubGlobal('fetch', fetchMock);

    renderWithRouter(<App />, { route: '/history' });

    expect(screen.getByText('Histórico de mensagens')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Histórico' })).toHaveAttribute(
      'aria-current',
      'page',
    );

    await waitFor(() => {
      expect(screen.getByLabelText('Buscar mensagens')).toBeInTheDocument();
    });
  });

  it('renders Admin when navigating to /admin', async () => {
    const fetchMock = mockFetchRoutes({
      '/api/admin/recipients': { page: 0, size: 20, total: 0, items: [] },
    });
    vi.stubGlobal('fetch', fetchMock);

    renderWithRouter(<App />, { route: '/admin' });

    expect(screen.getByRole('heading', { name: 'Administração' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Administração' })).toHaveAttribute(
      'aria-current',
      'page',
    );

    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'Disparar enfileiramento' })).toBeInTheDocument();
    });
  });

  it('renders 404 page for unknown route', () => {
    renderWithRouter(<App />, { route: '/not-found' });

    expect(screen.getByText('Página não encontrada')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Voltar para o painel' })).toBeInTheDocument();
  });
});
