package com.chargesquare.station.domain;

/** Bir istasyon id'si mevcut olmadığında fırlatılır. Web katmanında HTTP 404'e eşlenir. */
public class StationNotFoundException extends RuntimeException {

    public StationNotFoundException(Long stationId) {
        super("Station " + stationId + " not found");
    }
}
