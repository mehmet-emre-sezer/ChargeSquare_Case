package com.chargesquare.station.web.dto;

import com.chargesquare.station.domain.Connector;

/** Occupy/release operasyonları için minimal yanıt. */
public record ConnectorStatusView(
        Long connectorId,
        String status
) {
    public static ConnectorStatusView from(Connector connector) {
        return new ConnectorStatusView(connector.getId(), connector.getStatus().name());
    }
}
