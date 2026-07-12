package com.chargesquare.station.web;

import com.chargesquare.station.service.ConnectorService;
import com.chargesquare.station.web.dto.ConnectorView;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Bir istasyonun connector'larını listelemek için HTTP giriş noktası. */
@RestController
@RequestMapping("/stations")
public class StationController {

    private final ConnectorService connectorService;

    public StationController(ConnectorService connectorService) {
        this.connectorService = connectorService;
    }

    @GetMapping("/{id}/connectors")
    public List<ConnectorView> listConnectors(@PathVariable Long id) {
        return connectorService.listStationConnectors(id);
    }
}
