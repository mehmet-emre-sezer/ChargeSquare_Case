import { sessionApi } from './client.js'

/** Bir kullanıcının oturumlarını (status + cost) döner. */
export function getUserSessions(userId, token) {
  return sessionApi(`/users/${userId}/sessions`, { token })
}

/** Aktif bir oturumu durdurur ve makbuzu döner (yalnızca ADMIN). */
export function stopSession(sessionId, energyKwh, token) {
  return sessionApi(`/sessions/${sessionId}/stop`, {
    method: 'POST',
    body: { energyKwh },
    token,
  })
}
