import { Outlet, Link, useLocation } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { LayoutDashboard, PlusCircle, History, BarChart3, LogOut } from 'lucide-react';

const navItems = [
  { to: '/', label: 'Dashboard', icon: LayoutDashboard },
  { to: '/add', label: 'Add Expense', icon: PlusCircle },
  { to: '/history', label: 'History', icon: History },
  { to: '/analytics', label: 'Analytics', icon: BarChart3 },
];

export default function Layout() {
  const { user, logout } = useAuth();
  const location = useLocation();

  return (
    <div style={{ minHeight: '100vh', background: '#f9fafb' }}>
      <nav style={{
        background: '#1e293b', color: 'white', padding: '0 24px',
        display: 'flex', alignItems: 'center', justifyContent: 'space-between', height: 56
      }}>
        <Link to="/" style={{ color: 'white', textDecoration: 'none', fontWeight: 700, fontSize: 20 }}>
          💰 Budget Tracker
        </Link>
        <div style={{ display: 'flex', gap: 16, alignItems: 'center' }}>
          {navItems.map(({ to, label, icon: Icon }) => (
            <Link
              key={to}
              to={to}
              style={{
                color: location.pathname === to ? '#60a5fa' : '#cbd5e1',
                textDecoration: 'none', display: 'flex', alignItems: 'center', gap: 4, fontSize: 14
              }}
            >
              <Icon size={16} /> {label}
            </Link>
          ))}
          <span style={{ color: '#94a3b8', fontSize: 14, marginLeft: 16 }}>{user?.name}</span>
          <button
            onClick={logout}
            style={{
              background: 'none', border: 'none', color: '#94a3b8', cursor: 'pointer',
              display: 'flex', alignItems: 'center', gap: 4
            }}
          >
            <LogOut size={16} /> Logout
          </button>
        </div>
      </nav>
      <main style={{ maxWidth: 1200, margin: '0 auto', padding: 24 }}>
        <Outlet />
      </main>
    </div>
  );
}
