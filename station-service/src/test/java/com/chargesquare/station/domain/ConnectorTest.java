package com.chargesquare.station.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Connector'ın status geçiş invariant'larının davranış testleri. */
class ConnectorTest {

    private Connector availableConnector() {
        Tariff tariff = new Tariff(new BigDecimal("8.50"), new BigDecimal("2.00"), "TRY");
        return new Connector(new Station("Test"), tariff, "CCS2-DC", 60, ConnectorStatus.AVAILABLE);
    }

    @Test
    void occupy_whenAvailable_becomesOccupied() {
        Connector connector = availableConnector();

        connector.occupy();

        assertThat(connector.getStatus()).isEqualTo(ConnectorStatus.OCCUPIED);
    }

    @Test
    void occupy_whenAlreadyOccupied_isRejected() {
        Connector connector = availableConnector();
        connector.occupy();

        assertThatThrownBy(connector::occupy)
                .isInstanceOf(ConnectorNotAvailableException.class);
    }

    @Test
    void release_isIdempotent() {
        Connector connector = availableConnector();

        connector.release();

        assertThat(connector.getStatus()).isEqualTo(ConnectorStatus.AVAILABLE);
    }
}
