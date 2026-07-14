// İki backend servisine yapılan çağrıları tek yerde toplar: base URL, auth başlığı,
// ortak hata gövdesinin ({error, message}) çözümlenmesi.

const STATION_URL = import.meta.env.VITE_STATION_URL ?? 'http://localhost:8081'
const SESSION_URL = import.meta.env.VITE_SESSION_URL ?? 'http://localhost:8082'

async function request(baseUrl, path, { method = 'GET', body, token } = {}) {
  const headers = { 'Content-Type': 'application/json' }
  if (token) headers.Authorization = `Bearer ${token}`

  const response = await fetch(`${baseUrl}${path}`, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
  })

  const text = await response.text()
  const data = text ? JSON.parse(text) : null

  if (!response.ok) {
    // Backend'in ortak hata gövdesindeki mesajı kullanıcıya taşınır.
    throw new Error(data?.message || data?.error || `İstek başarısız (${response.status})`)
  }
  return data
}

export const stationApi = (path, options) => request(STATION_URL, path, options)
export const sessionApi = (path, options) => request(SESSION_URL, path, options)
