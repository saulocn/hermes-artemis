import { NavLink } from 'react-router-dom';

const links = [
  { to: '/', label: 'Painel', end: true },
  { to: '/compose', label: 'Nova mensagem', end: false },
  { to: '/history', label: 'Histórico', end: false },
  { to: '/admin', label: 'Administração', end: false },
];

export default function Nav() {
  return (
    <nav className="app-nav">
      <h1>Hermes</h1>
      {links.map((link) => (
        <NavLink
          key={link.to}
          to={link.to}
          end={link.end}
          className={({ isActive }) => (isActive ? 'active' : undefined)}
        >
          {link.label}
        </NavLink>
      ))}
    </nav>
  );
}
