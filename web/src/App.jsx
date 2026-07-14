import { useState } from 'react'
import { useAuth } from './auth/AuthContext.jsx'
import LoginPage from './pages/LoginPage.jsx'
import Layout from './components/Layout.jsx'
import ConnectorsPage from './pages/ConnectorsPage.jsx'
import SessionsPage from './pages/SessionsPage.jsx'

const NAV = [
  { key: 'connectors', label: "Connector'lar" },
  { key: 'sessions', label: 'Oturumlar' },
]

export default function App() {
  const { isAuthenticated } = useAuth()
  const [page, setPage] = useState('connectors')

  if (!isAuthenticated) {
    return <LoginPage />
  }

  return (
    <Layout nav={NAV} active={page} onNavigate={setPage}>
      {page === 'connectors' ? <ConnectorsPage /> : <SessionsPage />}
    </Layout>
  )
}
