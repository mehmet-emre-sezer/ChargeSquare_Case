import { useCallback, useEffect, useState } from 'react'
import { useAuth } from '../auth/AuthContext.jsx'
import { getUserSessions, stopSession } from '../api/sessions.js'
import Loading from '../components/Loading.jsx'
import ErrorBanner from '../components/ErrorBanner.jsx'
import Modal from '../components/Modal.jsx'

// Seed veride tek kullanıcı var.
const USER_ID = 7

function formatTime(iso) {
  return iso ? new Date(iso).toLocaleString('tr-TR') : '—'
}

function money(amount, currency) {
  return amount != null ? `${amount} ${currency}` : '—'
}

export default function SessionsPage() {
  const { token, isAdmin } = useAuth()
  const [sessions, setSessions] = useState(null)
  const [error, setError] = useState(null)
  const [receipt, setReceipt] = useState(null)
  const [stopping, setStopping] = useState(null)

  const load = useCallback(() => {
    setError(null)
    getUserSessions(USER_ID, token)
      .then(setSessions)
      .catch((err) => setError(err.message))
  }, [token])

  useEffect(() => {
    load()
  }, [load])

  if (error && !sessions) return <ErrorBanner message={error} />
  if (!sessions) return <Loading />

  return (
    <section>
      <h2 className="page__title">Oturumlar</h2>
      <ErrorBanner message={error} />

      <table className="table">
        <thead>
          <tr>
            <th>ID</th>
            <th>Connector</th>
            <th>Durum</th>
            <th>Maliyet</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {sessions.map((s) => (
            <tr key={s.sessionId} className="clickable" onClick={() => setReceipt(s)}>
              <td>{s.sessionId}</td>
              <td>{s.connectorId}</td>
              <td>
                <span className={`status status--${s.status.toLowerCase()}`}>{s.status}</span>
              </td>
              <td>{money(s.cost, s.currency)}</td>
              <td onClick={(e) => e.stopPropagation()}>
                {s.status === 'ACTIVE' && (
                  <button
                    className="btn btn--sm btn--primary"
                    disabled={!isAdmin}
                    title={isAdmin ? '' : 'Yalnızca ADMIN durdurabilir'}
                    onClick={() => setStopping(s)}
                  >
                    Durdur
                  </button>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      {receipt && <ReceiptModal session={receipt} onClose={() => setReceipt(null)} />}
      {stopping && (
        <StopModal
          session={stopping}
          token={token}
          onClose={() => setStopping(null)}
          onDone={() => {
            setStopping(null)
            load()
          }}
        />
      )}
    </section>
  )
}

function ReceiptModal({ session, onClose }) {
  return (
    <Modal title={`Makbuz — Oturum ${session.sessionId}`} onClose={onClose}>
      <dl className="receipt">
        <div><dt>Durum</dt><dd>{session.status}</dd></div>
        <div><dt>Connector</dt><dd>{session.connectorId}</dd></div>
        <div><dt>Enerji</dt><dd>{session.energyKwh != null ? `${session.energyKwh} kWh` : '—'}</dd></div>
        <div><dt>Maliyet</dt><dd>{money(session.cost, session.currency)}</dd></div>
        <div><dt>Başlangıç</dt><dd>{formatTime(session.startedAt)}</dd></div>
        <div><dt>Bitiş</dt><dd>{formatTime(session.endedAt)}</dd></div>
        <div><dt>Kalan bakiye</dt><dd>{money(session.walletBalanceAfter, session.currency)}</dd></div>
      </dl>
    </Modal>
  )
}

function StopModal({ session, token, onClose, onDone }) {
  const [energy, setEnergy] = useState('')
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  async function confirm() {
    setError(null)
    setBusy(true)
    try {
      await stopSession(session.sessionId, Number(energy), token)
      onDone()
    } catch (err) {
      setError(err.message)
      setBusy(false)
    }
  }

  return (
    <Modal title={`Oturum ${session.sessionId} durdur`} onClose={onClose}>
      <label className="field">
        <span>Teslim edilen enerji (kWh)</span>
        <input type="number" step="0.1" min="0" value={energy} autoFocus
               onChange={(e) => setEnergy(e.target.value)} />
      </label>
      <ErrorBanner message={error} />
      <button className="btn btn--primary" onClick={confirm} disabled={busy || energy === ''}>
        {busy ? 'Durduruluyor…' : 'Durdur ve faturala'}
      </button>
    </Modal>
  )
}
