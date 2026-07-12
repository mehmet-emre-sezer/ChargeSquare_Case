package com.chargesquare.station.web;

import com.chargesquare.station.service.ConnectorService;
import com.chargesquare.station.web.dto.ConnectorStatusView;
import com.chargesquare.station.web.dto.ConnectorView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Bir connector'ı okumak ve status'ünü değiştirmek için HTTP giriş noktaları. Bilerek ince tutulur. */
@RestController
@RequestMapping("/connectors")
public class ConnectorController {

    private final ConnectorService connectorService;

    public ConnectorController(ConnectorService connectorService) {
        this.connectorService = connectorService;
    }

    @GetMapping("/{id}")
    public ConnectorView getConnector(@PathVariable Long id) {
        return connectorService.getConnector(id);
    }

    // Internal operasyonlar, yalnızca start/stop sırasında Session Service çağırır.
    @PostMapping("/{id}/occupy")
    public ConnectorStatusView occupy(@PathVariable Long id) {
        return connectorService.occupy(id);
    }

    @PostMapping("/{id}/release")
    public ConnectorStatusView release(@PathVariable Long id) {
        return connectorService.release(id);
    }
}
