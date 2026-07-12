package com.chargesquare.session.client;

/** Station Service bilinmeyen bir connector için 404 döndüğünde fırlatılır. HTTP 404'e eşlenir. */
public class ConnectorNotFoundException extends RuntimeException {

    public ConnectorNotFoundException(Long connectorId) {
        super("Connector " + connectorId + " not found");
    }
}
