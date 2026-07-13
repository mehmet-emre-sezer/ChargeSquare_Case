package com.chargesquare.station;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Connector endpoint'lerini seed veriye karşı sınar (gömülü gerçek Postgres). Her test
 * transaction'da koşar ve geri alınır; böylece status değişiklikleri testler arasında sızmaz.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureEmbeddedDatabase(provider = AutoConfigureEmbeddedDatabase.DatabaseProvider.ZONKY)
@Transactional
class ConnectorEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getConnector_returnsStatusAndTariff() throws Exception {
        mockMvc.perform(get("/connectors/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.tariff.pricePerKwh").value(8.50));
    }

    @Test
    void getUnknownConnector_returns404() throws Exception {
        mockMvc.perform(get("/connectors/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("CONNECTOR_NOT_FOUND"));
    }

    @Test
    void occupyTwice_secondReturns409() throws Exception {
        mockMvc.perform(post("/connectors/10/occupy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OCCUPIED"));

        mockMvc.perform(post("/connectors/10/occupy"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONNECTOR_OCCUPIED"));
    }
}
