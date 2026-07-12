package com.chargesquare.station.web.dto;

import com.chargesquare.station.domain.Connector;

/** Tam connector okuma modeli: status ve tarifesi. */
public record ConnectorView(
        Long connectorId,
        Long stationId,
        String type,
        Integer powerKw,
        String status,
        TariffView tariff
) {
    public static ConnectorView from(Connector connector) {
        return new ConnectorView(
                connector.getId(),
                connector.getStation().getId(),
                connector.getType(),
                connector.getPowerKw(),
                connector.getStatus().name(),
                TariffView.from(connector.getTariff())
        );
    }
}
