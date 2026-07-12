package com.chargesquare.station.domain;

/** Bir connector id'si mevcut olmadığında fırlatılır. Web katmanında HTTP 404'e eşlenir. */
public class ConnectorNotFoundException extends RuntimeException {

    public ConnectorNotFoundException(Long connectorId) {
        super("Connector " + connectorId + " not found");
    }
}
