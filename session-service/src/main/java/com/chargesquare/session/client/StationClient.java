package com.chargesquare.session.client;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Station Service'e yapılan senkron REST çağrılarını tek bir yerde toplar.
 * HTTP detaylarını domain'den gizler ve Station yanıtlarını anlamlı exception'lara çevirir.
 */
@Component
public class StationClient {

    private final RestClient restClient;

    public StationClient(RestClient stationRestClient) {
        this.restClient = stationRestClient;
    }

    /** Connector'ın status'ünü ve tarifesini okur. Start yolundaki gerçek senkron çağrı. */
    public ConnectorSnapshot getConnector(Long connectorId) {
        try {
            return restClient.get()
                    .uri("/connectors/{id}", connectorId)
                    .retrieve()
                    .onStatus(status -> status.value() == 404,
                            (req, res) -> { throw new ConnectorNotFoundException(connectorId); })
                    .body(ConnectorSnapshot.class);
        } catch (ConnectorNotFoundException e) {
            throw e;
        } catch (RestClientException e) {
            throw new StationUnavailableException("Station Service unreachable on getConnector", e);
        }
    }

    /** Connector'ı OCCUPIED işaretler. Zaten dolu ise 409 -> ConnectorNotAvailableException. */
    public void occupy(Long connectorId) {
        try {
            restClient.post()
                    .uri("/connectors/{id}/occupy", connectorId)
                    .retrieve()
                    .onStatus(status -> status.value() == 404,
                            (req, res) -> { throw new ConnectorNotFoundException(connectorId); })
                    .onStatus(status -> status.value() == 409,
                            (req, res) -> { throw new ConnectorNotAvailableException(connectorId); })
                    .toBodilessEntity();
        } catch (ConnectorNotFoundException | ConnectorNotAvailableException e) {
            throw e;
        } catch (RestClientException e) {
            throw new StationUnavailableException("Station Service unreachable on occupy", e);
        }
    }

    /** Connector'ı AVAILABLE'a geri döndürür. Stop yolunda çağrılır. */
    public void release(Long connectorId) {
        try {
            restClient.post()
                    .uri("/connectors/{id}/release", connectorId)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new StationUnavailableException("Station Service unreachable on release", e);
        }
    }
}
