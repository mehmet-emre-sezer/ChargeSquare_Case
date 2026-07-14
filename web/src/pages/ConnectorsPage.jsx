import { useEffect, useState } from 'react'
import { useAuth } from '../auth/AuthContext.jsx'
import { getStationConnectors } from '../api/stations.js'
import Loading from '../components/Loading.jsx'
import ErrorBanner from '../components/ErrorBanner.jsx'

// Seed veride tek istasyon var; salt-okunur connector listesi.
const STATION_ID = 1

export default function ConnectorsPage() {
  const { token } = useAuth()
  const [connectors, setConnectors] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    let active = true
    getStationConnectors(STATION_ID, token)
      .then((data) => active && setConnectors(data))
      .catch((err) => active && setError(err.message))
    return () => {
      active = false
    }
  }, [token])

  if (error) return <ErrorBanner message={error} />
  if (!connectors) return <Loading />

  return (
    <section>
      <h2 className="page__title">İstasyon connector'ları</h2>
      <table className="table">
        <thead>
          <tr>
            <th>ID</th>
            <th>Tip</th>
            <th>Güç</th>
            <th>Durum</th>
            <th>Tarife</th>
          </tr>
        </thead>
        <tbody>
          {connectors.map((c) => (
            <tr key={c.connectorId}>
              <td>{c.connectorId}</td>
              <td>{c.type}</td>
              <td>{c.powerKw} kW</td>
              <td>
                <span className={`status status--${c.status.toLowerCase()}`}>{c.status}</span>
              </td>
              <td>
                {c.tariff.pricePerKwh} {c.tariff.currency}/kWh + {c.tariff.startFee}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  )
}
