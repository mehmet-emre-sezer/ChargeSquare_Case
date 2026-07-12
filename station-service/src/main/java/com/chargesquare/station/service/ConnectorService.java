package com.chargesquare.station.service;

import com.chargesquare.station.domain.Connector;
import com.chargesquare.station.domain.ConnectorNotFoundException;
import com.chargesquare.station.domain.StationNotFoundException;
import com.chargesquare.station.repository.ConnectorRepository;
import com.chargesquare.station.repository.StationRepository;
import com.chargesquare.station.web.dto.ConnectorStatusView;
import com.chargesquare.station.web.dto.ConnectorView;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Connector'lar için okuma ve status değişiklikleri. Burada Station Service tek doğruluk kaynağıdır. */
@Service
public class ConnectorService {

    private static final Logger log = LoggerFactory.getLogger(ConnectorService.class);

    private final ConnectorRepository connectors;
    private final StationRepository stations;

    public ConnectorService(ConnectorRepository connectors, StationRepository stations) {
        this.connectors = connectors;
        this.stations = stations;
    }

    @Transactional(readOnly = true)
    public ConnectorView getConnector(Long connectorId) {
        Connector connector = connectors.findWithTariffById(connectorId)
                .orElseThrow(() -> new ConnectorNotFoundException(connectorId));
        return ConnectorView.from(connector);
    }

    @Transactional(readOnly = true)
    public List<ConnectorView> listStationConnectors(Long stationId) {
        if (!stations.existsById(stationId)) {
            throw new StationNotFoundException(stationId);
        }
        return connectors.findByStationIdWithTariff(stationId).stream()
                .map(ConnectorView::from)
                .toList();
    }

    /** Bir connector'ı OCCUPIED işaretler. Yalnızca oturum başlangıcında Session Service çağırır. */
    @Transactional
    public ConnectorStatusView occupy(Long connectorId) {
        Connector connector = connectors.findByIdForUpdate(connectorId)
                .orElseThrow(() -> new ConnectorNotFoundException(connectorId));
        connector.occupy();
        log.info("Connector {} occupied", connectorId);
        return ConnectorStatusView.from(connector);
    }

    /** Bir connector'ı AVAILABLE'a geri döndürür. Oturum durdurulduğunda Session Service çağırır. */
    @Transactional
    public ConnectorStatusView release(Long connectorId) {
        Connector connector = connectors.findByIdForUpdate(connectorId)
                .orElseThrow(() -> new ConnectorNotFoundException(connectorId));
        connector.release();
        log.info("Connector {} released", connectorId);
        return ConnectorStatusView.from(connector);
    }
}
