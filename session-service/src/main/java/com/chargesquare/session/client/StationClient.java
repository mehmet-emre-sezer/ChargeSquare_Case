package com.chargesquare.session.client;

import com.chargesquare.session.security.JwtService;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Station Service'e yapılan senkron REST çağrılarını tek bir yerde toplar.
 * HTTP detaylarını domain'den gizler ve Station yanıtlarını anlamlı exception'lara çevirir.
 * Station'ın korumalı uçlarına (occupy/release) erişebilmek için her çağrıya ADMIN rolünde
 * bir servis token'ı ekler — RBAC tablosundaki "servis-servis" satırı.
 */
@Component
public class StationClient {

    private static final String SERVICE_SUBJECT = "session-service";

    private final RestClient restClient;
    private final JwtService jwtService;

    public StationClient(RestClient stationRestClient, JwtService jwtService) {
        this.restClient = stationRestClient;
        this.jwtService = jwtService;
    }

    private String serviceToken() {
        return "Bearer " + jwtService.issueToken(SERVICE_SUBJECT, "ADMIN");
    }

    /** Connector'ın status'ünü ve tarifesini okur. Start yolundaki gerçek senkron çağrı. */
    public ConnectorSnapshot getConnector(Long connectorId) {
        try {
            return restClient.get()
                    .uri("/connectors/{id}", connectorId)
                    .header(HttpHeaders.AUTHORIZATION, serviceToken())
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
                    .header(HttpHeaders.AUTHORIZATION, serviceToken())
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
                    .header(HttpHeaders.AUTHORIZATION, serviceToken())
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new StationUnavailableException("Station Service unreachable on release", e);
        }
    }
}
