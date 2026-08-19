import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { EmailPreview } from './EmailPreview';

describe('EmailPreview', () => {
  it('renders an iframe with correct attributes', () => {
    const html = '<html><body><h1>Test</h1></body></html>';
    render(<EmailPreview html={html} />);

    const iframe = screen.getByTitle('Pré-visualização do e-mail');
    expect(iframe).toBeInTheDocument();
    expect(iframe).toHaveAttribute('sandbox', '');
    expect(iframe).toHaveAttribute('srcDoc', html);
    expect(iframe).toHaveClass('email-preview');
  });

  it('updates srcdoc when html prop changes', () => {
    const { rerender } = render(<EmailPreview html="<p>First</p>" />);
    const iframe = screen.getByTitle('Pré-visualização do e-mail') as HTMLIFrameElement;
    expect(iframe.srcdoc).toBe('<p>First</p>');

    rerender(<EmailPreview html="<p>Second</p>" />);
    expect(iframe.srcdoc).toBe('<p>Second</p>');
  });

  it('maintains sandbox="" empty string (all restrictions on)', () => {
    render(<EmailPreview html="<p>Test</p>" />);
    const iframe = screen.getByTitle('Pré-visualização do e-mail') as HTMLIFrameElement;
    // Empty string means all restrictions are on (no allow-scripts, no allow-same-origin, etc.)
    // jsdom sets sandbox to an empty TokenList when attribute is empty string
    expect(iframe.getAttribute('sandbox')).toBe('');
  });
});
