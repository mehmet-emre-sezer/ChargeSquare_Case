import { createContext, useContext, useState } from 'react'
import { login as loginRequest } from '../api/auth.js'

// Oturum durumunu (token + rol) tutar ve login/logout sağlar.
// Token localStorage'da saklanır — basitlik için; XSS/CSRF trade-off'u DESIGN'da not edildi.

const AuthContext = createContext(null)
const STORAGE_KEY = 'chargesquare.auth'

function loadStored() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

export function AuthProvider({ children }) {
  const [auth, setAuth] = useState(loadStored)

  async function login(username, password) {
    const { token, role } = await loginRequest(username, password)
    const next = { token, role, username }
    localStorage.setItem(STORAGE_KEY, JSON.stringify(next))
    setAuth(next)
  }

  function logout() {
    localStorage.removeItem(STORAGE_KEY)
    setAuth(null)
  }

  const value = {
    token: auth?.token ?? null,
    role: auth?.role ?? null,
    username: auth?.username ?? null,
    isAuthenticated: Boolean(auth?.token),
    isAdmin: auth?.role === 'ADMIN',
    login,
    logout,
  }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used within AuthProvider')
  return context
}
