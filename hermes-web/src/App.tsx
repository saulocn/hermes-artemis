import { Route, Routes } from 'react-router-dom';
import Nav from './components/Nav';
import Dashboard from './pages/Dashboard';
import Compose from './pages/Compose';
import History from './pages/History';
import Admin from './pages/Admin';

export default function App() {
  return (
    <div className="app-shell">
      <Nav />
      <main className="app-main">
        <Routes>
          <Route path="/" element={<Dashboard />} />
          <Route path="/compose" element={<Compose />} />
          <Route path="/history" element={<History />} />
          <Route path="/admin" element={<Admin />} />
          <Route
            path="*"
            element={
              <div>
                <h2 className="page-title">Página não encontrada</h2>
                <p>
                  <a href="/">Voltar para o painel</a>
                </p>
              </div>
            }
          />
        </Routes>
      </main>
    </div>
  );
}
