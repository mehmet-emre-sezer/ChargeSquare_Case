import { stationApi } from './client.js'

/** Bir istasyonun connector'larını (status + tarife) döner. */
export function getStationConnectors(stationId, token) {
  return stationApi(`/stations/${stationId}/connectors`, { token })
}
