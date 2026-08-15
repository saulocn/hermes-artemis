import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import Nav from './Nav';
import { renderWithRouter } from '../test/helpers';

describe('Nav component', () => {
  it('renders all four navigation links', () => {
    renderWithRouter(<Nav />);

    expect(screen.getByRole('link', { name: 'Painel' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Nova mensagem' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Histórico' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Administração' })).toBeInTheDocument();
  });

  it('marks the Painel link as active when on home route', () => {
    renderWithRouter(<Nav />, { route: '/' });

    const painelLink = screen.getByRole('link', { name: 'Painel' });
    expect(painelLink).toHaveAttribute('aria-current', 'page');
  });

  it('marks the Nova mensagem link as active when on /compose route', () => {
    renderWithRouter(<Nav />, { route: '/compose' });

    const composeLink = screen.getByRole('link', { name: 'Nova mensagem' });
    expect(composeLink).toHaveAttribute('aria-current', 'page');
  });

  it('marks the Histórico link as active when on /history route', () => {
    renderWithRouter(<Nav />, { route: '/history' });

    const historyLink = screen.getByRole('link', { name: 'Histórico' });
    expect(historyLink).toHaveAttribute('aria-current', 'page');
  });

  it('marks the Administração link as active when on /admin route', () => {
    renderWithRouter(<Nav />, { route: '/admin' });

    const adminLink = screen.getByRole('link', { name: 'Administração' });
    expect(adminLink).toHaveAttribute('aria-current', 'page');
  });
});
