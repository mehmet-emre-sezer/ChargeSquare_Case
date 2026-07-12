package com.chargesquare.session.client;

/** Connector AVAILABLE değilken (Station 409) fırlatılır. HTTP 409'a eşlenir. */
public class ConnectorNotAvailableException extends RuntimeException {

    public ConnectorNotAvailableException(Long connectorId) {
        super("Connector " + connectorId + " is not AVAILABLE");
    }
}
