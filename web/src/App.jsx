import { useAuth } from './auth/AuthContext.jsx'
import LoginPage from './pages/LoginPage.jsx'
import Layout from './components/Layout.jsx'

// Kimlik doğrulanmamışsa login; doğrulanmışsa panel iskeleti.
// İstasyon ve oturum ekranları Parça C'de navigasyona eklenir.
const NAV = []

export default function App() {
  const { isAuthenticated } = useAuth()

  if (!isAuthenticated) {
    return <LoginPage />
  }

  return (
    <Layout nav={NAV} active={null} onNavigate={() => {}}>
      <p className="state">Giriş başarılı. İstasyon ve oturum ekranları hazırlanıyor…</p>
    </Layout>
  )
}
