import { useAuth } from '../hooks/useAuth';

export default function Login() {
  const { login, isLoading } = useAuth();

  return (
    <div style={{
      minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center',
      background: 'linear-gradient(135deg, #4a1d96 0%, #7c3aed 50%, #a78bfa 100%)',
    }}>
      <div style={{
        background: 'white', borderRadius: 16, padding: 48, textAlign: 'center',
        boxShadow: '0 25px 50px rgba(0,0,0,0.25)', maxWidth: 400, width: '100%',
      }}>
        <div style={{ fontSize: 48, marginBottom: 16 }}>🌸</div>
        <h1 style={{ fontSize: 28, fontWeight: 700, marginBottom: 8 }}>Lily AI</h1>
        <p style={{ color: '#6b7280', marginBottom: 32 }}>
          Your Personal AI Assistant
        </p>
        <button
          onClick={login}
          disabled={isLoading}
          style={{
            background: '#7c3aed', color: 'white', border: 'none',
            padding: '14px 32px', borderRadius: 8, fontSize: 16,
            fontWeight: 600, cursor: 'pointer', width: '100%',
            display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8,
          }}
        >
          <svg width="20" height="20" viewBox="0 0 24 24" fill="white">
            <path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92a5.06 5.06 0 0 1-2.2 3.32v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.1z"/>
            <path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/>
            <path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"/>
            <path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"/>
          </svg>
          Sign in with Google
        </button>
      </div>
    </div>
  );
}
