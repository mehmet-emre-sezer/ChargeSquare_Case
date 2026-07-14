import { useAuth } from '../auth/AuthContext.jsx'

// Üst bar: marka, sekme navigasyonu, kullanıcı rozeti ve çıkış.
export default function Layout({ nav = [], active, onNavigate, children }) {
  const { username, role, logout } = useAuth()

  return (
    <div className="app">
      <header className="topbar">
        <span className="topbar__brand">ChargeSquare</span>

        <nav className="topbar__nav">
          {nav.map((item) => (
            <button
              key={item.key}
              className={`navlink ${active === item.key ? 'navlink--active' : ''}`}
              onClick={() => onNavigate(item.key)}
            >
              {item.label}
            </button>
          ))}
        </nav>

        <div className="topbar__user">
          <span className={`badge badge--${role === 'ADMIN' ? 'admin' : 'viewer'}`}>{role}</span>
          <span className="topbar__username">{username}</span>
          <button className="btn btn--ghost" onClick={logout}>Çıkış</button>
        </div>
      </header>

      <main className="content">{children}</main>
    </div>
  )
}
