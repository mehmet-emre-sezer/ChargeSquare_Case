import { useState } from 'react'
import { useAuth } from '../auth/AuthContext.jsx'
import ErrorBanner from '../components/ErrorBanner.jsx'

export default function LoginPage() {
  const { login } = useAuth()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(false)

  async function onSubmit(event) {
    event.preventDefault()
    setError(null)
    setLoading(true)
    try {
      await login(username, password)
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="login">
      <form className="card login__card" onSubmit={onSubmit}>
        <h1 className="login__title">ChargeSquare Panel</h1>
        <p className="login__subtitle">Operasyon paneli</p>

        <label className="field">
          <span>Kullanıcı adı</span>
          <input value={username} onChange={(e) => setUsername(e.target.value)} autoFocus />
        </label>
        <label className="field">
          <span>Şifre</span>
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} />
        </label>

        <ErrorBanner message={error} />

        <button className="btn btn--primary" type="submit" disabled={loading}>
          {loading ? 'Giriş yapılıyor…' : 'Giriş yap'}
        </button>

        <p className="login__hint">Demo: admin / admin123 · viewer / viewer123</p>
      </form>
    </div>
  )
}
