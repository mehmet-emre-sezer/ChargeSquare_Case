import { sessionApi } from './client.js'

/** Giriş: kullanıcı adı + şifre karşılığında { token, role } döner. */
export function login(username, password) {
  return sessionApi('/auth/login', { method: 'POST', body: { username, password } })
}
