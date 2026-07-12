package com.chargesquare.station.domain;

/** AVAILABLE olmayan bir connector'ı occupy etmeye çalışınca fırlatılır. HTTP 409'a eşlenir. */
public class ConnectorNotAvailableException extends RuntimeException {

    public ConnectorNotAvailableException(Long connectorId) {
        super("Connector " + connectorId + " is not AVAILABLE");
    }
}
